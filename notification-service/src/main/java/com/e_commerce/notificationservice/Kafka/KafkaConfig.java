package com.e_commerce.notificationservice.Kafka;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // ─────────────────────────────────────────────────
    // 1. KafkaTemplate: necesario para que el Recoverer
    //    pueda PUBLICAR mensajes al topic DLQ.
    //    Spring lo autoconfigura, pero lo declaramos
    //    para tener control total sobre él.
    // ─────────────────────────────────────────────────
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(
            ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // ─────────────────────────────────────────────────
    // 2. DeadLetterPublishingRecoverer: el "recuperador".
    //    Se invoca cuando se agotan todos los reintentos.
    //    Su función: tomar el mensaje fallido y publicarlo
    //    en el topic DLQ.
    // ─────────────────────────────────────────────────
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<String, Object> kafkaTemplate) {

        return new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                // (record, exception) -> destino personalizado
                (record, ex) -> new TopicPartition(
                        record.topic() + ".DLT",
                        record.partition()        // misma partición que el original (garantiza orden dentro de la partición)
                )
        );
    }

    // ─────────────────────────────────────────────────
    // 3. BackOff: define la estrategia de reintentos.
    //    ExponentialBackOff hace que los reintentos sean
    //    progresivamente más espaciados:
    //    intento 1 → 1s, intento 2 → 2s, intento 3 → 4s
    //    Esto protege servicios externos (DB, APIs) de
    //    ser bombardeados en caso de fallo masivo.
    // Después de 5 intentos fallidos → el mensaje va al Dead Letter Topic
    // ─────────────────────────────────────────────────
    @Bean
    public ExponentialBackOff exponentialBackOff() {
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1_000L);   // espera inicial: 1 segundo
        backOff.setMultiplier(2.0);           // se duplica en cada reintento
        backOff.setMaxElapsedTime(10_000L);   // tras 10s totales, se rinde y va a DLQ
        return backOff;
    }

    // ─────────────────────────────────────────────────
    // 4. DefaultErrorHandler: el manejador de errores
    //    central. Combina el BackOff (cuándo/cuánto
    //    reintentar) con el Recoverer (qué hacer al
    //    agotar reintentos).
    //    Reemplaza a SeekToCurrentErrorHandler
    //    (deprecado desde Spring Kafka 2.8).
    // ─────────────────────────────────────────────────
    @Bean
    @Primary
    public DefaultErrorHandler defaultErrorHandler(
            DeadLetterPublishingRecoverer recoverer,
            ExponentialBackOff backOff) {

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Reintento #{} para topic={} partition={} offset={}",
                        deliveryAttempt,
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        ex.getMessage()));

        // Errores NO recuperables: si el mensaje tiene datos
        // inválidos (deserialización, validación), no tiene
        // sentido reintentarlo → va directo a DLQ sin esperar.
        handler.addNotRetryableExceptions(
                DeserializationException.class,   // JSON malformado
                IllegalArgumentException.class    // datos de negocio inválidos
        );

        return handler;
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        // Deserializadores: los mensajes de Debezium son JSON en texto plano.
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // earliest: si no hay offset guardado (primera ejecución o grupo nuevo),
        // empieza desde el mensaje más antiguo del topic.
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Desactivamos el auto-commit: el offset se confirma solo cuando el
        // mensaje se procesa con éxito (ver KafkaConsumer donde hacemos ack.acknowledge())
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        //Si hay productores transaccionales, no leer mensajes de transacciones abortadas.
        //config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        // Máximo de mensajes por llamada a poll()
        // Ajústalo según cuánto tarda en procesar cada mensaje tu lógica de negocio.
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);

        // Tiempo máximo entre dos llamadas a poll(). Si el procesamiento es lento, aumentarlo para evitar rebalanceos.
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);

        // Tiempo sin heartbeat antes de declarar muerto al consumer. No debe ser menor que el GC pause máximo esperado.
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 45000);

        // Debe ser ~1/3 de session.timeout.ms. El heartbeat corre en un hilo separado del procesamiento.
        //config.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);

        //config.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, "CooperativeStickyAssignor");

        // El broker espera hasta tener al menos N bytes para responder al fetch. Reduce peticiones vacías.
        config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);

        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Container Factory: envuelve el ConsumerFactory con configuración
     * adicional de concurrencia, acks y manejo de errores.
     *
     * Los @KafkaListener usan este factory por defecto.
     */
    @Bean
    @Primary
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            @Qualifier("defaultErrorHandler") DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        // Inyecta el handler de errores en la fábrica.
        // Sin esta línea, Spring usaría el handler por defecto
        // (que solo loguea y no envía a DLQ).
        factory.setCommonErrorHandler(errorHandler);

        // Número de threads (consumers) en paralelo.
        // Debe ser <= número de particiones del topic para que todos trabajen.
        // Con 3 particiones → 3 threads, cada uno procesa 1 partición.
        factory.setConcurrency(3);

        // MANUAL_IMMEDIATE: el consumer llama a ack.acknowledge() manualmente
        // cuando termina de procesar. Si no llama a acknowledge(), Kafka
        // reenvía el mensaje en el siguiente poll.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        return factory;
    }

    @Bean
    public DefaultErrorHandler dlqErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, exception) -> {
                    log.error(
                            "❌ Error procesando mensaje del DLQ. Topic: {}, Partition: {}, Offset: {}, Key: {}. Error: {}",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            record.key(),
                            exception.getMessage()
                    );
                    // Aquí podrías persistir en una tabla "dead_messages"
                    // para revisión manual, en lugar de re-publicar a Kafka.
                },
                new org.springframework.util.backoff.FixedBackOff(0L, 0L) // sin reintentos
        );
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> dlqListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            @Qualifier("dlqErrorHandler") DefaultErrorHandler dlqErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(dlqErrorHandler);
        factory.setConcurrency(1); // normalmente el DLQ no necesita mucha concurrencia
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        return factory;
    }
}
