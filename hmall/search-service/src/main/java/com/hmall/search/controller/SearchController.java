package com.hmall.search.controller;

import cn.hutool.core.util.StrUtil;
import com.hmall.common.domain.PageDTO;
import com.hmall.search.domain.dto.ItemDTO;
import com.hmall.search.domain.query.ItemPageQuery;
import com.hmall.search.util.HandleResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Api(tags = "搜索相关接口")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Slf4j
public class SearchController {
    private final RestHighLevelClient client;

    @ApiOperation("搜索商品")
    @GetMapping("/list")
    public PageDTO<ItemDTO> search(ItemPageQuery query) throws IOException {
        // 分页查询
        SearchRequest request = new SearchRequest("items");
        // 构建bool查询
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        // 添加过滤条件
        if (StrUtil.isNotBlank(query.getKey())) {
            queryBuilder.must(QueryBuilders.matchQuery("name", query.getKey()));
        }
        if (StrUtil.isNotBlank(query.getBrand())) {
            queryBuilder.filter(QueryBuilders.termQuery("brand", query.getBrand()));
        }
        if (StrUtil.isNotBlank(query.getCategory())) {
            queryBuilder.filter(QueryBuilders.termQuery("category", query.getCategory()));
        }
        if (query.getMaxPrice() != null) {
            queryBuilder.filter(QueryBuilders.rangeQuery("price").gte(query.getMinPrice()).lte(query.getMaxPrice()));
        }
        // 包装 function_score 提升广告权重
        QueryBuilders.functionScoreQuery(
                new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                QueryBuilders.termQuery("isAD", true),
                                ScoreFunctionBuilders.weightFactorFunction(100)
                        )
                }
        ).boostMode(CombineFunction.MULTIPLY);
        // 发送请求
        request.source().query(queryBuilder)
                // 分页：页面长度
                .size(query.getPageSize())
                // 分页：页码
                .from(query.from());
        // sort 按照得分降序
        request.source().sort("_score", SortOrder.DESC);
//        List<OrderItem> orders = query.toMpPage("updateTime", false).getOrders();
//        for (OrderItem orderItem : orders) {
//            request.source().sort(orderItem.getColumn(), orderItem.isAsc() ? SortOrder.ASC : SortOrder.DESC);
//        }
        SearchResponse searchResponse = client.search(request, RequestOptions.DEFAULT);
        PageDTO<ItemDTO> itemDTOPageDTO = HandleResponse.ESResponseAsDTO(searchResponse, query);
        return itemDTOPageDTO;
        //           Page<Item> result = itemService.lambdaQuery()
//               .like(StrUtil.isNotBlank(query.getKey()), Item::getName, query.getKey())
//               .eq(StrUtil.isNotBlank(query.getBrand()), Item::getBrand, query.getBrand())
//               .eq(StrUtil.isNotBlank(query.getCategory()), Item::getCategory, query.getCategory())
//               .eq(Item::getStatus, 1)
//               .between(query.getMaxPrice() != null, Item::getPrice, query.getMinPrice(), query.getMaxPrice())
//               .page(query.toMpPage("update_time", false));
//           return PageDTO.of(result, ItemDTO.class);
    }

    @ApiOperation("条件聚合")
    @PostMapping("/filters")
    public Map<String, List<String>> searchFilter(@RequestBody ItemPageQuery query) throws IOException {
        SearchRequest request = new SearchRequest("items");
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        if (StrUtil.isNotBlank(query.getKey())) {
            // 判断关键字类型：品牌、分类或其他
            String key = query.getKey();
            // 构建should查询来判断关键字类型
            BoolQueryBuilder keyTypeQuery = QueryBuilders.boolQuery();
            keyTypeQuery.should(QueryBuilders.termQuery("brand", key));
            keyTypeQuery.should(QueryBuilders.termQuery("category", key));
            keyTypeQuery.minimumShouldMatch(1);

            // 执行一次额外的搜索来判断关键字类型
            SearchRequest keyTypeRequest = new SearchRequest("items");
            keyTypeRequest.source().query(keyTypeQuery).size(0);
            // 添加聚合来判断关键字是品牌还是分类
            keyTypeRequest.source()
                    .aggregation(AggregationBuilders.terms("brand_agg").field("brand").
                            includeExclude(new org.elasticsearch.search.aggregations.bucket.terms.IncludeExclude(key, null))
                            .size(1))
                    .aggregation(AggregationBuilders.terms("category_agg").field("category")
                            .includeExclude(new org.elasticsearch.search.aggregations.bucket.terms.IncludeExclude(key, null))
                            .size(1));

            SearchResponse keyTypeResponse = client.search(keyTypeRequest, RequestOptions.DEFAULT);
            // 分析聚合结果判断关键字类型
            boolean isBrand = keyTypeResponse.getAggregations().get("brand_agg") != null &&
                    !((ParsedStringTerms) keyTypeResponse.getAggregations().get("brand_agg")).getBuckets().isEmpty();
            boolean isCategory = keyTypeResponse.getAggregations().get("category_agg") != null &&
                    !((ParsedStringTerms) keyTypeResponse.getAggregations().get("category_agg")).getBuckets().isEmpty();


            if (isBrand) {
                // 如果关键字是品牌，则作为品牌过滤条件
                queryBuilder.filter(QueryBuilders.termQuery("brand", key));
            } else if (isCategory) {
                // 如果关键字是分类，则作为分类过滤条件
                queryBuilder.filter(QueryBuilders.termQuery("category", key));
            } else {
                // 否则作为商品名称搜索
                queryBuilder.must(QueryBuilders.matchQuery("name", key));
            }
        }
        if (StrUtil.isNotBlank(query.getBrand())) {
            queryBuilder.filter(QueryBuilders.termQuery("brand", query.getBrand()));
        }
        if (StrUtil.isNotBlank(query.getCategory())) {
            queryBuilder.filter(QueryBuilders.termQuery("category", query.getCategory()));
        }
        if (query.getMaxPrice() != null) {
            queryBuilder.filter(QueryBuilders.rangeQuery("price").gte(query.getMinPrice()).lte(query.getMaxPrice()));
        }
        // 发送请求
        request.source().query(queryBuilder);
        // 聚合
        String brandAgg = "brandAgg";
        String categoryAgg = "categoryAgg";
        if (StrUtil.isNotBlank(query.getBrand())) {
            request.source().aggregation(AggregationBuilders.terms(brandAgg).field("category")).size(20);
        }
        if (StrUtil.isNotBlank(query.getCategory())) {
            request.source().aggregation(AggregationBuilders.terms(categoryAgg).field("brand")).size(20);
        }
        SearchResponse searchResponse = client.search(request, RequestOptions.DEFAULT);
        return HandleResponse.ESResponseAsAggregation(searchResponse, brandAgg, categoryAgg);
    }

//    @ApiOperation("新增商品")
//    @PostMapping("/{id}")
//    public void saveItemById(@PathVariable("id") Long id) throws Exception {
//        // 1.根据id查询商品数据
//        Item item = itemService.getById(id);
//        if (item == null || item.getStatus() != 1) {
//            log.info("商品不存在或已下架");
//            return;
//        }
//        // 2.转换为文档类型
//        ItemDoc itemDoc = BeanUtil.copyProperties(item, ItemDoc.class);
//        // 3.将ItemDTO转json
//        String doc = JSONUtil.toJsonStr(itemDoc);
//
//        // 1.准备Request对象
//        IndexRequest request = new IndexRequest("items").id(itemDoc.getId());
//        // 2.准备Json文档
//        request.source(doc, XContentType.JSON);
//        // 3.发送请求
//        try {
//            client.index(request, RequestOptions.DEFAULT);
//            log.info("商品保存成功");
//        } catch (Exception e) {
//            log.error("商品保存失败");
//            throw new RuntimeException(e);
//        }
//    }
//    @ApiOperation("根据id查询商品")
//    @GetMapping("/{id}")
//    public ItemDoc queryItemById(@PathVariable("id") Long id) throws IOException {
//        // 1.准备Request对象
//        GetRequest request = new GetRequest("items").id(String.valueOf(id));
//        // 2.发送请求
//        GetResponse response = client.get(request, RequestOptions.DEFAULT);
//        // 3.获取响应结果中的source
//        String json = response.getSourceAsString();
//
//        ItemDoc itemDoc = JSONUtil.toBean(json, ItemDoc.class);
//        System.out.println("itemDoc= " + itemDoc);
//        return itemDoc;
//    }
}
