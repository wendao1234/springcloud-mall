package com.hmall.search.listener;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.hmall.search.domain.dto.ItemDTO;
import com.hmall.search.domain.po.ItemDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

import static com.hmall.search.constants.MQConstants.*;

@RequiredArgsConstructor
@Slf4j
@Transactional
@Component
public class ListenerItem {
    private final RestHighLevelClient client;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = CREATE_ITEM_QUEUE, durable = "true"),
            exchange = @Exchange(value = ITEM_EXCHANGE),
            key = CREATE_ITEM_ROUTING_KEY
    ))
    public void saveItem(ItemDTO item) {
        // 1.转换为文档类型
        ItemDoc itemDoc = BeanUtil.copyProperties(item, ItemDoc.class);
        // 2.将ItemDTO转json
        String doc = JSONUtil.toJsonStr(itemDoc);

        // 1.准备Request对象
        IndexRequest request = new IndexRequest("items").id(itemDoc.getId());
        // 2.准备Json文档
        request.source(doc, XContentType.JSON);
        // 3.发送请求
        try {
            client.index(request, RequestOptions.DEFAULT);
            log.info("商品保存成功");
        } catch (Exception e) {
            log.error("商品保存失败");
            throw new RuntimeException(e);
        }

    }
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = UPDATE_ITEM_QUEUE, durable = "true"),
            exchange = @Exchange(value = ITEM_EXCHANGE),
            key = UPDATE_ITEM_ROUTING_KEY
    ))
    public void updateItem(ItemDTO item) throws IOException {
        // 1.转换为文档类型
        ItemDoc itemDoc = BeanUtil.copyProperties(item, ItemDoc.class);
        // 2.将ItemDTO转json
        String doc = JSONUtil.toJsonStr(itemDoc);

        // 1.准备Request对象
        UpdateRequest request = new UpdateRequest("items",itemDoc.getId());
        // 2.准备Json文档
        request.doc(doc, XContentType.JSON);
        // 3.发送请求
        try {
            client.update(request, RequestOptions.DEFAULT);
            log.info("商品更新成功");
        } catch (Exception e) {
            log.error("商品更新失败");
            throw new RuntimeException(e);
        }

    }
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = DELETE_ITEM_QUEUE, durable = "true"),
            exchange = @Exchange(value = ITEM_EXCHANGE),
            key = DELETE_ITEM_ROUTING_KEY
    ))
    public void deleteItem(Long itemId) {
        // 1.准备Request对象
        DeleteRequest request = new DeleteRequest("items", itemId.toString());
        // 2.发送请求
        try {
            client.delete(request, RequestOptions.DEFAULT);
            log.info("商品删除成功");
        } catch (Exception e) {
            log.error("商品删除失败");
            throw new RuntimeException(e);
        }
    }
}