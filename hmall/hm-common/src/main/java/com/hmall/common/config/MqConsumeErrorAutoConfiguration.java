package com.hmall.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.rabbitmq.listener.simple.retry.enabled", havingValue = "true")
public class MqConsumeErrorAutoConfiguration {

    @Value("${spring.application.name}")
    private String serviceName;

    /**
     * 声明错误处理的Direct交换机
     */
    @Bean
    public DirectExchange errorExchange() {
        return new DirectExchange("error.direct");
    }

    /**
     * 声明错误队列，名称为 微服务名 + ".error.queue"
     */
    @Bean
    public Queue errorQueue() {
        return new Queue(serviceName + ".error.queue");
    }

    /**
     * 将错误队列与交换机绑定，RoutingKey为微服务名
     */
    @Bean
    public Binding errorBinding() {
        return BindingBuilder.bind(errorQueue())
                .to(errorExchange())
                .with(serviceName);
    }

    /**
     * 声明RepublishMessageRecoverer，用于将消费失败的消息投递到指定交换机
     */
    @Bean
    public RepublishMessageRecoverer republishMessageRecoverer(AmqpTemplate amqpTemplate) {
        return new RepublishMessageRecoverer(amqpTemplate, "error.direct", serviceName);
    }
}