package com.example.aisearch.service.indexing.domain;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsAliasRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesResponse;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.util.ObjectBuilder;
import com.example.aisearch.config.AiSearchProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AliasSwitcherTest {

    @Mock
    private ElasticsearchClient esClient;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    @Test
    @DisplayName("read alias가 여러 인덱스를 가리키면 최신 버전 인덱스를 선택한다")
    void shouldSelectLatestIndexWhenAliasTargetsMultipleIndices() throws IOException {
        when(esClient.indices()).thenReturn(indicesClient);
        doReturn(new BooleanResponse(true)).when(indicesClient).existsAlias(anyExistsAliasFunction());
        doReturn(GetAliasResponse.of(b -> b.result(Map.of(
                "food-products-kure-v1-v20260319134454", emptyIndexAliases(),
                "food-products-kure-v1-v20260319134510", emptyIndexAliases()
        )))).when(indicesClient).getAlias(anyGetAliasFunction());

        AliasSwitcher aliasSwitcher = new AliasSwitcher(esClient, testProperties("food-products-kure-v1-read"));

        String currentIndex = aliasSwitcher.findCurrentAliasedIndex();

        assertEquals("food-products-kure-v1-v20260319134510", currentIndex);
    }

    @Test
    @DisplayName("alias 전환 시 기존 alias 대상 인덱스들을 모두 제거하고 새 인덱스를 추가한다")
    @SuppressWarnings("unchecked")
    void shouldRemoveAllExistingAliasTargetsOnSwap() throws IOException {
        when(esClient.indices()).thenReturn(indicesClient);
        doReturn(new BooleanResponse(false)).when(indicesClient).exists(anyExistsFunction());
        doReturn(GetAliasResponse.of(b -> b.result(Map.of(
                "food-products-kure-v1-v20260319134454", emptyIndexAliases(),
                "food-products-kure-v1-v20260319134510", emptyIndexAliases()
        )))).when(indicesClient).getAlias(anyGetAliasFunction());
        doReturn(UpdateAliasesResponse.of(b -> b.acknowledged(true))).when(indicesClient).updateAliases(anyUpdateAliasesFunction());

        AliasSwitcher aliasSwitcher = new AliasSwitcher(esClient, testProperties("food-products-kure-v1-read"));
        aliasSwitcher.swapReadAlias("food-products-kure-v1-v20260319134510", "food-products-kure-v1-v20260319135000");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Function<UpdateAliasesRequest.Builder, ObjectBuilder<UpdateAliasesRequest>>> captor =
                ArgumentCaptor.forClass((Class) Function.class);
        verify(indicesClient).updateAliases(captor.capture());

        UpdateAliasesRequest request = captor.getValue()
                .apply(new UpdateAliasesRequest.Builder())
                .build();

        assertEquals(3, request.actions().size());
        assertEquals("food-products-kure-v1-v20260319134454", request.actions().get(0).remove().index());
        assertEquals("food-products-kure-v1-v20260319134510", request.actions().get(1).remove().index());
        assertEquals("food-products-kure-v1-v20260319135000", request.actions().get(2).add().index());
    }

    private static AiSearchProperties testProperties(String readAlias) {
        return new AiSearchProperties(
                "http://127.0.0.1:9210",
                "elastic",
                "elastic",
                "food-products-kure-v1",
                readAlias,
                "food-synonyms",
                "classpath:data/synonyms.txt",
                "classpath:data/synonyms-regression.txt",
                null,
                "classpath:/model/KURE-v1",
                0.0,
                60,
                60,
                1000,
                5000,
                2,
                3
        );
    }

    private static IndexAliases emptyIndexAliases() {
        return IndexAliases.of(b -> b.aliases(Map.of()));
    }

    @SuppressWarnings("unchecked")
    private static Function<ExistsAliasRequest.Builder, ObjectBuilder<ExistsAliasRequest>> anyExistsAliasFunction() {
        return (Function<ExistsAliasRequest.Builder, ObjectBuilder<ExistsAliasRequest>>) any(Function.class);
    }

    @SuppressWarnings("unchecked")
    private static Function<GetAliasRequest.Builder, ObjectBuilder<GetAliasRequest>> anyGetAliasFunction() {
        return (Function<GetAliasRequest.Builder, ObjectBuilder<GetAliasRequest>>) any(Function.class);
    }

    @SuppressWarnings("unchecked")
    private static Function<ExistsRequest.Builder, ObjectBuilder<ExistsRequest>> anyExistsFunction() {
        return (Function<ExistsRequest.Builder, ObjectBuilder<ExistsRequest>>) any(Function.class);
    }

    @SuppressWarnings("unchecked")
    private static Function<UpdateAliasesRequest.Builder, ObjectBuilder<UpdateAliasesRequest>> anyUpdateAliasesFunction() {
        return (Function<UpdateAliasesRequest.Builder, ObjectBuilder<UpdateAliasesRequest>>) any(Function.class);
    }
}
