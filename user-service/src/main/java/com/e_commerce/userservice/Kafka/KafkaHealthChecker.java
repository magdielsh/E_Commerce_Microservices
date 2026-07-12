package com.e_commerce.userservice.Kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaHealthChecker {

    private final KafkaAdmin kafkaAdmin;

    private volatile boolean kafkaAvailable = true;

    //@Scheduled(fixedDelay = 5000)
    public boolean checkKafkaHealth() {
        try {
            // Intenta listar topics con timeout corto
            AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties());
            adminClient.listTopics(new ListTopicsOptions().timeoutMs(3000)).names().get();
            adminClient.close();

            if (!kafkaAvailable) {
                log.info("✅ Kafka volvió a estar disponible");
            }
            kafkaAvailable = true;

        } catch (Exception e) {
            if (kafkaAvailable) {
                log.error("🔴 Kafka NO disponible: {}", e.getMessage());
            }
            kafkaAvailable = false;
        }
        return kafkaAvailable;
    }
}
