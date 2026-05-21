package com.hmall.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.common.utils.RabbitMqHelper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(value = {MessageConverter.class, RabbitTemplate.class})
public class MqConfig {

    @Bean
//    @ConditionalOnBean(ObjectMapper.class)
    public MessageConverter messageConverter(ObjectMapper mapper) {
        // 1.定义消息转换器
        Jackson2JsonMessageConverter jackson2JsonMessageConverter = new Jackson2JsonMessageConverter(mapper);
        // 2.配置自动创建消息id，用于识别不同消息
        jackson2JsonMessageConverter.setCreateMessageIds(true);
        return jackson2JsonMessageConverter;
    }

    @Bean
    public RabbitMqHelper rabbitMqHelper(RabbitTemplate rabbitTemplate) {
        return new RabbitMqHelper(rabbitTemplate);
    }
}
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.hmall.common.utils.UserContext;
//import org.springframework.amqp.core.Message;
//import org.springframework.amqp.core.MessageProperties;
//import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
//import org.springframework.amqp.support.converter.MessageConversionException;
//import org.springframework.amqp.support.converter.MessageConverter;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//@ConditionalOnClass({MessageConverter.class, Jackson2JsonMessageConverter.class})
//public class MQMessageConverterConfig {
//
//    @Bean
//    public MessageConverter messageConverter(ObjectMapper objectMapper) {
//        // 1.定义消息转换器
//        Jackson2JsonMessageConverter jackson2JsonMessageConverter = new Jackson2JsonMessageConverter(objectMapper) {
//            @Override
//            protected Message createMessage(Object objectToConvert, MessageProperties messageProperties) throws MessageConversionException {
//                // 在发送消息前，将当前用户信息添加到消息头中
//                Long userId = UserContext.getUser();
//                if (userId != null) {
//                    messageProperties.setHeader("userId", userId);
//                }
//                return super.createMessage(objectToConvert, messageProperties);
//            }
//
//            @Override
//            public Object fromMessage(Message message) throws MessageConversionException {
//                // 在接收消息时，从消息头中恢复用户信息到UserContext
//                Object userHeader = message.getMessageProperties().getHeader("userId");
//                if (userHeader != null) {
//                    if (userHeader instanceof Long) {
//                        UserContext.setUser((Long) userHeader);
//                    } else if (userHeader instanceof Integer) {
//                        UserContext.setUser(((Integer) userHeader).longValue());
//                    } else if (userHeader instanceof String) {
//                        UserContext.setUser(Long.parseLong((String) userHeader));
//                    }
//                }
////                if (userHeader != null && userHeader instanceof Long) {
////                    UserContext.setUser((Long) userHeader);
////                }
//                try {
//                    return super.fromMessage(message);
//                } catch (Exception e) {
//                    throw new MessageConversionException("转换消息失败", e);
//                } finally {
//                    // 清理UserContext，避免线程污染
//                    UserContext.removeUser();
//                }
//            }
//        };
//        // 2.配置自动创建消息id，用于识别不同消息，也可以在业务中基于ID判断是否是重复消息
//        jackson2JsonMessageConverter.setCreateMessageIds(true);
//        return jackson2JsonMessageConverter;
//    }
//}
