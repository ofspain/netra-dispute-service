package com.netstra.disputes.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.exceptions.AppDataAccessException;
import com.netra.commons.models.Dispute;
import com.netra.commons.requests.SortedSearchParams;
import com.netra.commons.responses.PagedResult;
import com.netra.commons.util.EnumUtils;
import com.netstra.disputes.requests.DisputeSearchParams;
import lombok.RequiredArgsConstructor;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class DisputeDao {
    private final JdbcClient jdbcClient;
    private final NamedParameterJdbcTemplate namedParamsJdbcTemplate;


    private final ObjectMapper objectMapper;

    public PagedResult<Dispute> findProcessingDisputes(int page, int size) {
        String json = jdbcClient.sql("SELECT get_pending_processing_disputes(?, ?) as result")
                .param(1, size)
                .param(2, page * size)
                .query(String.class)
                .single();

        return getDisputePagedResult(json);
    }

    public PagedResult<Dispute> findDisputes(DisputeSearchParams params) {
        String sortColumn = params.getSortColumn();

        SortedSearchParams.SortDirection sortDirectionType = params.getSortDirection();
        String sortDirection = null != sortDirectionType ? sortDirectionType.name() : "ASC";




        String sql = """
        SELECT fetch_disputes(
            :p_limit,
            :p_offset,
            :p_is_finalized,
            :p_is_resolved,
            :p_resolved_in_customer_favor,
            :p_locked,
            :p_current_state,
            :p_previous_state,
            :p_disputant_identity_uuid,
            :p_issuer_code,
            :p_acquirer_code,
            :p_merchant_code,
            :p_beneficiary_code,
            :p_switcher_code,
            :p_created_via,
            :p_date_from,
            :p_date_to,
            :p_date_mark_legit_from,
            :p_date_mark_legit_to,
            :p_sort_column,
            :p_sort_direction
        ) AS result
        """;

        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("p_limit", params.getPageSize())
                .addValue("p_offset", (params.getPageNum() - 1) * params.getPageSize())
                .addValue("p_is_finalized", paramsBoolean(params.isFinalized()))
                .addValue("p_is_resolved", paramsBoolean(params.isResolved()))
                .addValue("p_resolved_in_customer_favor", paramsBoolean(params.isResolvedInCustomerFavor()))
                .addValue("p_locked", paramsBoolean(params.isLocked()))
                .addValue("p_current_state", params.getCurrentState() != null ? params.getCurrentState().name() : null)
                .addValue("p_previous_state", params.getPreviousState() != null ? params.getPreviousState().name() : null)
                .addValue("p_disputant_identity_uuid", params.getDisputant() != null ? params.getDisputant().getIdentity().getIdentityUuid() : null)
                .addValue("p_issuer_code", params.getIssuerCode())
                .addValue("p_acquirer_code", params.getAcquirerCode())
                .addValue("p_merchant_code", params.getMerchantCode())
                .addValue("p_beneficiary_code", params.getBeneficiaryCode())
                .addValue("p_switcher_code", params.getSwitcherCode())
                .addValue("p_created_via", params.getCreatedBy() != null ? params.getCreatedBy().name() : null)
                .addValue("p_date_from", params.getStartDate())
                .addValue("p_date_to", params.getEndDate())
                .addValue("p_date_mark_legit_from", params.getDisputeMarkedLegitTimeStartDate())
                .addValue("p_date_mark_legit_to", params.getDisputeMarkedLegitTimeEndDate())
                .addValue("p_sort_column", sortColumn)
                .addValue("p_sort_direction", sortDirection);

        return namedParamsJdbcTemplate.queryForObject(sql, paramSource, (rs, rowNum) -> {
            String json = rs.getString("result");

            return getDisputePagedResult(json);
        });
    }

    @NotNull
    private PagedResult<Dispute> getDisputePagedResult(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            List<Dispute> disputes = objectMapper.readValue(
                    root.path("data").toString(),
                    new TypeReference<List<Dispute>>() {}
            );

            JsonNode meta = root.path("meta");
            PagedResult.Metadata metadata = new PagedResult.Metadata(
                    meta.path("total").asLong(),
                    meta.path("limit").asInt(),
                    meta.path("offset").asInt(),
                    meta.path("has_more").asBoolean()
            );

            return new PagedResult<>(disputes, metadata);

        } catch (JsonProcessingException e) {
            throw new AppDataAccessException("Failed to parse disputes", e);
        }
    }

    private Boolean paramsBoolean(boolean value) {
        return value ? Boolean.TRUE : null;
    }



    private Dispute mapRow(ResultSet rs, int rowNum) throws SQLException {
        Dispute dispute = new Dispute();
        dispute.setId(rs.getLong("id"));
        dispute.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        dispute.setCurrentState(EnumUtils.fromStringIgnoreCase(DisputeState.class, rs.getString("current_state")));
        // map all other fields...
        return dispute;
    }
}
