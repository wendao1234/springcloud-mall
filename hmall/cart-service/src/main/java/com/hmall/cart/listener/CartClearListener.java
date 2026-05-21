package com.hmall.cart.listener;

import com.hmall.cart.service.ICartService;
import com.hmall.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class CartClearListener {
    public final ICartService cartService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "cart.clear.queue", durable = "true"),
            exchange = @Exchange(name = "trade.topic", type = ExchangeTypes.TOPIC),
            key = "order.create"
    ))
    public void listenCartClear(Map<String, Object> messageMap) {
        log.info("收到清理购物车商品消息，准备清理购物车商品：{}", messageMap);
        Object userIdObj = messageMap.get("userId");
        Long userId;
        if (userIdObj instanceof Long) {
            userId = (Long) userIdObj;
        } else if (userIdObj instanceof String) {
            userId = Long.valueOf((String) userIdObj);
        } else {
            log.warn("获取清理购物车消息中的userId类型错误，导致userId为空");
            return;
        }
        UserContext.setUser(userId);

        // 将 ArrayList 转换为 Set，而不是强制转换
        Collection<Long> itemIdsCollection = (Collection<Long>) messageMap.get("itemIds");
        Set<Long> itemIds = itemIdsCollection != null ? new HashSet<>(itemIdsCollection) : Collections.emptySet();
//        Set<Long> itemIds = (Set<Long>) messageMap.get("itemIds");
        cartService.removeByItemIds(itemIds);
        log.info("清理购物车商品成功，userId：{}，itemIds：{}", userId, itemIds);
        UserContext.removeUser();
    }
}
