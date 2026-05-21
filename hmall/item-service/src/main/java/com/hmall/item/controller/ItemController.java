package com.hmall.item.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.domain.PageQuery;
import com.hmall.common.utils.BeanUtils;
import com.hmall.item.domain.dto.ItemDTO;
import com.hmall.item.domain.dto.OrderDetailDTO;
import com.hmall.item.domain.po.Item;
import com.hmall.item.service.IItemService;
import io.seata.spring.annotation.GlobalTransactional;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "商品管理相关接口")
@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {

    private final IItemService itemService;
    private final RabbitTemplate rabbitTemplate;

    @ApiOperation("分页查询商品")
    @GetMapping("/page")
    public PageDTO<ItemDTO> queryItemByPage(PageQuery query) {
        // 1.分页查询
        Page<Item> result = itemService.page(query.toMpPage("update_time", false));
        // 2.封装并返回
        return PageDTO.of(result, ItemDTO.class);
    }

    @ApiOperation("根据id批量查询商品")
    @GetMapping
    public List<ItemDTO> queryItemByIds(@RequestParam("ids") List<Long> ids) {
        return itemService.queryItemByIds(ids);
    }

    @ApiOperation("根据id查询商品")
    @GetMapping("{id}")
    public ItemDTO queryItemById(@PathVariable("id") Long id) {
        return BeanUtils.copyBean(itemService.getById(id), ItemDTO.class);
    }

    @ApiOperation("新增商品")
    @PostMapping
    @GlobalTransactional
    public void saveItem(@RequestBody ItemDTO itemDTO) {
        // 新增
        // 获取最后一个商品的id
        Long lastId = itemService.lambdaQuery().select(Item::getId).orderByDesc(Item::getId).last("limit 1").one().getId();
        itemDTO.setId(lastId + 1L);
        itemService.save(BeanUtils.copyBean(itemDTO, Item.class));

        System.out.println("新增商品id:" + lastId + 1);
        // mq发送消息，触发商品索引更新
        rabbitTemplate.convertAndSend("item_exchange", "item.save", itemDTO);
    }

    @ApiOperation("更新商品状态")
    @PutMapping("/status/{id}/{status}")
    public void updateItemStatus(@PathVariable("id") Long id, @PathVariable("status") Integer status) {
        Item item = new Item();
        item.setId(id);
        item.setStatus(status);
        itemService.updateById(item);
    }

    @ApiOperation("更新商品")
    @PutMapping
    @GlobalTransactional
    public void updateItem(@RequestBody ItemDTO item) {
        // 不允许修改商品状态，所以强制设置为null，更新时，就会忽略该字段
        item.setStatus(null);
        // 更新
        itemService.updateById(BeanUtils.copyBean(item, Item.class));
        // mq发送消息，触发商品索引更新
        rabbitTemplate.convertAndSend("item_exchange", "item.update", item);
    }

    @ApiOperation("根据id删除商品")
    @DeleteMapping("{id}")
    @GlobalTransactional
    public void deleteItemById(@PathVariable("id") Long id) {
        itemService.removeById(id);
        // mq发送消息，触发商品索引删除
        rabbitTemplate.convertAndSend("item_exchange", "item.delete", id);
    }

    @ApiOperation("批量扣减库存")
    @PutMapping("/stock/deduct")
    public void deductStock(@RequestBody List<OrderDetailDTO> items) {
        itemService.deductStock(items);
    }

    @ApiOperation("批量恢复库存")
    @PutMapping("/stock/revert")
    public void revertStock(@RequestBody List<OrderDetailDTO> items) {
        itemService.restoreStock(items);
    }
}
