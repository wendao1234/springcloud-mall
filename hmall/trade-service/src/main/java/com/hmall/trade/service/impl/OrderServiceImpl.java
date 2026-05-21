package com.hmall.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.client.ItemClient;
import com.hmall.api.dto.ItemDTO;
import com.hmall.api.dto.OrderDetailDTO;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.utils.UserContext;
import com.hmall.trade.constants.MQConstants;
import com.hmall.trade.domain.dto.OrderFormDTO;
import com.hmall.trade.domain.po.Order;
import com.hmall.trade.domain.po.OrderDetail;
import com.hmall.trade.mapper.OrderDetailMapper;
import com.hmall.trade.mapper.OrderMapper;
import com.hmall.trade.service.IOrderDetailService;
import com.hmall.trade.service.IOrderService;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {
    private final ItemClient itemService;
    private final RabbitTemplate rabbitTemplate;
//    private final cartClient cartService;
    private final IOrderDetailService detailService;

    @Override
    @GlobalTransactional
    public Long createOrder(OrderFormDTO orderFormDTO) {
        // 1.订单数据
        Order order = new Order();
        // 1.1.查询商品
        List<com.hmall.trade.domain.dto.OrderDetailDTO> details1 = orderFormDTO.getDetails();
        List<OrderDetailDTO> detailDTOS = details1.stream()
                .map(detail -> new OrderDetailDTO()
                        .setItemId(detail.getItemId())
                        .setNum(detail.getNum()))
                .collect(Collectors.toList());
        // 1.2.获取商品id和数量的Map
        Map<Long, Integer> itemNumMap = detailDTOS.stream()
                .collect(Collectors.toMap(OrderDetailDTO::getItemId, OrderDetailDTO::getNum));
        Set<Long> itemIds = itemNumMap.keySet();
        // 1.3.查询商品
        List<ItemDTO> items = itemService.queryItemByIds(itemIds);
        if (items == null || items.size() < itemIds.size()) {
            throw new BadRequestException("商品不存在");
        }
        // 1.4.基于商品价格、购买数量计算商品总价：totalFee
        int total = 0;
        for (ItemDTO item : items) {
            total += item.getPrice() * itemNumMap.get(item.getId());
        }
        order.setTotalFee(total);
        // 1.5.其它属性
        order.setPaymentType(orderFormDTO.getPaymentType());
        order.setUserId(UserContext.getUser());
        order.setStatus(1);
        // 1.6.将Order写入数据库order表中
        save(order);

        // 2.保存订单详情
        List<OrderDetail> details = buildDetails(order.getId(), items, itemNumMap);
        detailService.saveBatch(details);

        // 3.扣减库存
        try {
            itemService.deductStock(detailDTOS);
        } catch (Exception e) {
            throw new RuntimeException("库存不足！");
        }
        // 4.清理购物车商品
        // cartService.deleteCartItemByIds(new ArrayList<> (itemIds));
        // 构建发送消息体
        Map<String, Object>  messageMap = new HashMap<>();
        messageMap.put("userId", UserContext.getUser());
        messageMap.put("itemIds", itemIds);
        try {
            log.info("清理购物车商品消息发送成功，消息体：{}", messageMap);
            rabbitTemplate.convertAndSend("trade.topic", "order.create", messageMap);
        } catch (Exception e) {
            log.error("清理购物车商品消息发送失败，消息体：{}", messageMap, e);
        }

        //5.发送延迟消息，检测订单支付状态
        rabbitTemplate.convertAndSend(
                MQConstants.DELAY_EXCHANGE_NAME,
                MQConstants.DELAY_ORDER_KEY,
                order.getId(),
                message ->{
                    message.getMessageProperties().setDelay(10000); // 延迟10秒发送;
                    return message;
                } );

        return order.getId();
    }

    @Override
    public void markOrderPaySuccess(Long orderId) {
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(2);
        order.setPayTime(LocalDateTime.now());
        updateById(order);
    }

    @Override
    @GlobalTransactional
    public void cancelOrder(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            log.error("订单不存在，订单ID: {}", orderId);
            return;
        }
        // 1.判断订单状态
        if (order.getStatus() != 1) {
            log.error("订单状态不是待支付状态，不能取消");
            return;
        }
        // 2.取消订单
        order.setStatus(5);
        order.setCloseTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        updateById(order);
        // 3.准备恢复库存
        // 3.1根据订单id查询订单详情
        List<OrderDetail> details = detailService.lambdaQuery()
                .eq(OrderDetail::getOrderId, orderId)
                .list();
        // 3.2构建OrderDetailDTO集合
        List<OrderDetailDTO> detailDTOS = details.stream()
                .map(detail -> new OrderDetailDTO()
                        .setItemId(detail.getItemId())
                        .setNum(detail.getNum()))
                .collect(Collectors.toList());
        // 4.恢复库存
        itemService.revertStock(detailDTOS);
    }

    private List<OrderDetail> buildDetails(Long orderId, List<ItemDTO> items, Map<Long, Integer> numMap) {
        List<OrderDetail> details = new ArrayList<>(items.size());
        for (ItemDTO item : items) {
            OrderDetail detail = new OrderDetail();
            detail.setName(item.getName());
            detail.setSpec(item.getSpec());
            detail.setPrice(item.getPrice());
            detail.setNum(numMap.get(item.getId()));
            detail.setItemId(item.getId());
            detail.setImage(item.getImage());
            detail.setOrderId(orderId);
            details.add(detail);
        }
        return details;
    }
}
