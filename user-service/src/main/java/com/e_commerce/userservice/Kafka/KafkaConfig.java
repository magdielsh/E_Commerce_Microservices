package com.e_commerce.userservice.Kafka;

import com.e_commerce.userservice.Dto.UserCreatedValidationEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@EnableKafka
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

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
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        // Máximo de mensajes por llamada a poll()
        // Ajústalo según cuánto tarda en procesar cada mensaje tu lógica de negocio.
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);

        // Tiempo máximo entre dos llamadas a poll(). Si el procesamiento es lento, aumentarlo para evitar rebalanceos.
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);

        // Tiempo sin heartbeat antes de declarar muerto al consumer. No debe ser menor que el GC pause máximo esperado.
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 45000);

        // Debe ser ~1/3 de session.timeout.ms. El heartbeat corre en un hilo separado del procesamiento.
        config.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);

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
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        // Número de threads (consumers) en paralelo.
        // Debe ser <= número de particiones del topic para que todos trabajen.
        // Con 3 particiones → 3 threads, cada uno procesa 1 partición.
        factory.setConcurrency(3);

        // MANUAL_IMMEDIATE: el consumer llama a ack.acknowledge() manualmente
        // cuando termina de procesar. Si no llama a acknowledge(), Kafka
        // reenvía el mensaje en el siguiente poll.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // ─────────────────────────────────────────────────────────────────
        // ESTRATEGIA DE REINTENTOS CON BACKOFF EXPONENCIAL
        //
        // Si el procesamiento falla (excepción), en lugar de reenviarlo
        // inmediatamente (lo que podría saturar el sistema), esperamos
        // un tiempo creciente entre cada intento:
        //   Intento 1: espera 1s
        //   Intento 2: espera 2s
        //   Intento 3: espera 4s
        //   Intento 4: espera 8s
        //   Intento 5: espera 10s (límite máximo)
        //
        // Después de 5 intentos fallidos → el mensaje va al Dead Letter Topic
        // ─────────────────────────────────────────────────────────────────
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(5);
        backOff.setInitialInterval(1_000L);   // Primer reintento: 1 segundo
        backOff.setMultiplier(2.0);           // Cada intento duplica la espera
        backOff.setMaxInterval(10_000L);      // Máximo 10 segundos de espera

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                // Dead Letter Topic Publisher: cuando se agotan los reintentos,
                // el mensaje se manda al topic "{topicOriginal}.DLT" automáticamente.
                // Desde ahí puedes analizarlo, corregirlo y reencolar manualmente.
                (record, exception) -> {
                    log.error(
                            "❌ Mensaje no procesable tras reintentos. " +
                                    "Topic: {}, Partition: {}, Offset: {}, Key: {}. Error: {}",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            record.key(),
                            exception.getMessage()
                    );
                    // En producción aquí enviarías a un DLT con KafkaTemplate
                    // o guardarías en una tabla de errores para revisión manual
                },
                backOff
        );

        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

//    @Bean
//    public ProducerFactory<String, UserCreatedValidationEvent> producerFactory() {
//        Map<String, Object> props = new HashMap<>();
//        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
//
//        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
//        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
//
//        // ⚡ CLAVE: limitar cuánto espera para conectar/obtener metadata
//        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);        // 5s máximo bloqueado
//        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);  // timeout de la request
//        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 5000);// timeout total entrega
//
//        // Reintentos
//        props.put(ProducerConfig.RETRIES_CONFIG, 3);
//        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
//
//        // Garantías de entrega
//        props.put(ProducerConfig.ACKS_CONFIG, "all");
//        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
//
//        return new DefaultKafkaProducerFactory<>(props);
//    }
//
//    @Bean
//    public KafkaTemplate<String, UserCreatedValidationEvent> kafkaTemplate() {
//        return new KafkaTemplate<>(producerFactory());
//    }

//    @Bean
//    public NewTopic userCreatedTopic() {
//        return TopicBuilder.name("user-created")
//                .partitions(1)
//                .replicas(1)
//                .build();
//    }
}
