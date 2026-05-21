package com.hmall.search.util;

import cn.hutool.json.JSONUtil;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.utils.BeanUtils;
import com.hmall.search.domain.dto.ItemDTO;
import com.hmall.search.domain.po.ItemDoc;
import com.hmall.search.domain.query.ItemPageQuery;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 解析响应简单的查询所有文档并解析原文档为json字符串并转（函数复用）

public class HandleResponse {
    public static PageDTO<ItemDTO> ESResponseAsDTO(SearchResponse response, ItemPageQuery query) {
        SearchHits hits = response.getHits();
        List<ItemDoc> itemList = new ArrayList<>();
        long total = 0;
        if (hits.getTotalHits() != null) {
            total = hits.getTotalHits().value;
            System.out.println("总记录数=========>" + total);
        }
        SearchHit[] searchHits = hits.getHits();
        for (SearchHit hit : searchHits) {
            //将json字符串转换为对象获取json格式的数据
            String json = hit.getSourceAsString();
            //将json字符串转换为对象
            ItemDoc itemDoc = JSONUtil.toBean(json, ItemDoc.class);
            itemList.add(itemDoc);
//            System.out.println("itemDoc=========>" + itemDoc);
        }
        List<ItemDTO> itemDTOList = BeanUtils.copyList(itemList, ItemDTO.class);
        long pageSize = query.getPageSize().longValue();
        PageDTO<ItemDTO> itemDTOPageDTO = new PageDTO<>(total, pageSize, itemDTOList);
        return itemDTOPageDTO;
    }

    public static Map<String, List<String>> ESResponseAsAggregation(SearchResponse response, String... fields) {
        Aggregations aggregations = response.getAggregations();
        List<String> categoryList = List.of("手机", "曲面电视", "拉杆箱", "休闲鞋", "休闲鞋", "硬盘", "真皮包");
        List<String> brandList = List.of("希捷", "小米", "华为", "oppo", "新秀丽", "Apple", "锤子");
        // 添加空值检查
        if (aggregations == null) {
            return Map.of("category", categoryList, "brand", brandList);
        }

        Terms brandAgg = aggregations.get(fields[0]);
        Terms categoryAgg = aggregations.get(fields[1]);

        // 对每个聚合结果进行空值检查
        brandList = brandAgg != null ?
                brandAgg.getBuckets().stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList()) :
                Collections.emptyList();

        categoryList = categoryAgg != null ?
                categoryAgg.getBuckets().stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList()) :
                Collections.emptyList();
        return Map.of("category", brandList, "brand", categoryList);
    }
}
