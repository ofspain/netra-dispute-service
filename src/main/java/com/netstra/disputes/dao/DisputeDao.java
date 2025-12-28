package com.netstra.disputes.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netra.commons.enums.*;
import com.netra.commons.exceptions.AppDataAccessException;
import com.netra.commons.models.Dispute;
import com.netra.commons.requests.SortedSearchParams;
import com.netra.commons.responses.PagedResult;
import com.netra.commons.util.BasicUtil;
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

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class DisputeDao {
    private final JdbcClient jdbcClient;
    private final NamedParameterJdbcTemplate namedParamsJdbcTemplate;


    private final ObjectMapper objectMapper;

    // -------------------- FIND BY ID or LOG CODE --------------------
    public Optional<Dispute> findByIdOrLogCode(Long id, String logCode) {
        String sql = "SELECT fetch_dispute_dynamic_where(" +
                "p_limit => 1, " +
                "p_offset => 0, " +
                "p_where_clause => :whereClause)";

        String whereClause;
        if (id != null) {
            whereClause = "d.id = " + id;
        } else if (logCode != null && !logCode.isBlank()) {
            whereClause = "d.log_code = '" + logCode + "'";
        } else {
            //todo: use app dedicated exception
            throw new IllegalArgumentException("Either id or logCode must be provided");
        }

        List<Dispute> disputes = jdbcClient.sql(sql)
                .param("whereClause", whereClause)
                .query((rs, rowNum) -> mapRow(rs, rowNum)) // map each row to a Dispute
                .list(); // collect all rows into a list

        return disputes.isEmpty() ? Optional.empty() : Optional.of(disputes.get(0));
    }

    // -------------------- SAVE --------------------
    public Dispute save(Dispute dispute) {
        String sql = "SELECT * FROM upsert_dispute(" +
                "p_id => :id, " +
                "p_transaction_id => :transactionId, " +
                "p_dispute_marked_legit_time => :disputeMarkedLegitTime, " +
                "p_current_state => :currentState, " +
                "p_created_via => :createdVia, " +
                "p_disputant_identity_uuid => :disputantUuid, " +
                "p_disputant_type => :disputantType, " +
                "p_disputant_domain_code => :disputantDomainCode, " +
                "p_note => :note, " +
                "p_issuer_code => :issuerCode, " +
                "p_acquirer_code => :acquirerCode, " +
                "p_merchant_code => :merchantCode, " +
                "p_beneficiary_code => :beneficiaryCode, " +
                "p_switcher_code => :switcherCode, " +
                "p_locked => :locked, " +
                "p_is_finalized => :isFinalized, " +
                "p_is_resolved => :isResolved, " +
                "p_resolved_in_customer_favor => :resolvedInCustomerFavor)";

        return jdbcClient.sql(sql)
                .param("id", dispute.getId())
                .param("transactionId", dispute.getTransaction() != null ? dispute.getTransaction().getId() : null)
                .param("disputeMarkedLegitTime", dispute.getDisputeMarkedLegitTime())
                .param("currentState", dispute.getCurrentState() != null ? dispute.getCurrentState().name() : null)
                .param("createdVia", dispute.getCreatedVia() != null ? dispute.getCreatedVia().name() : null)
                .param("disputantUuid", dispute.getCreatedBy().getIdentity().getIdentityUuid())
                .param("disputantType", dispute.getCreatedBy().getDisputantType().name())
                .param("disputantDomainCode", dispute.getCreatedBy().getIdentity().getDomainCode())
                .param("note", dispute.getNote())
                .param("issuerCode", dispute.getIssuerCode())
                .param("acquirerCode", dispute.getAcquirerCode())
                .param("merchantCode", dispute.getMerchantCode())
                .param("beneficiaryCode", dispute.getBeneficiaryCode())
                .param("switcherCode", dispute.getSwitcherCode())
                .param("locked", dispute.isLocked())
                .param("isFinalized", dispute.isFinalized())
                .param("isResolved", dispute.isResolved())
                .param("resolvedInCustomerFavor", dispute.isResolvedInCustomerFavor())
                .query((rs, rowNum) -> mapRow(rs, rowNum))   // map each row
                .optional()                                  // get first row as Optional
                .orElseThrow(() -> new IllegalStateException("Failed to save dispute"));//todo: change to app specific exception

    }

    // -------------------- UPDATE --------------------
    public Dispute update(Dispute dispute, Long id) {
        dispute.setId(id);
        return save(dispute); // upsert handles both insert & update
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

        // ---------------- BaseEntity ----------------
        dispute.setId(rs.getLong("id"));
        Timestamp createdAtTs = rs.getTimestamp("created_at");
        if (createdAtTs != null) dispute.setCreatedAt(createdAtTs.toLocalDateTime());

        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        if (updatedAtTs != null) dispute.setUpdatedAt(updatedAtTs.toLocalDateTime());

        // ---------------- Dispute Core ----------------
        dispute.setLogCode(rs.getString("log_code"));
        dispute.setCurrentState(EnumUtils.fromStringIgnoreCase(DisputeState.class, rs.getString("current_state")));
        dispute.setPreviousState(EnumUtils.fromStringIgnoreCase(DisputeState.class, rs.getString("previous_state")));
        dispute.setDisputeMode(EnumUtils.fromStringIgnoreCase(DisputeMode.class, rs.getString("dispute_mode")));
        dispute.setLocked(rs.getBoolean("locked"));

        // ---------------- Transaction Info ----------------
        Timestamp transactionDateTs = rs.getTimestamp("transaction_date");
        if (transactionDateTs != null) dispute.setTransactionDate(transactionDateTs.toLocalDateTime());

        BigDecimal transactionAmount = rs.getBigDecimal("transaction_amount");
        dispute.setTransactionAmount(transactionAmount);

        dispute.setTransactionAction(EnumUtils.fromStringIgnoreCase(TransactionAction.class, rs.getString("transaction_action")));
        dispute.setTransactionPaymentRail(EnumUtils.fromStringIgnoreCase(PaymentRail.class, rs.getString("transaction_payment_rail")));
        dispute.setTransactionInstrument(EnumUtils.fromStringIgnoreCase(TransactionInstrument.class, rs.getString("transaction_instrument")));

        // ---------------- Creation Context ----------------
        dispute.setCreatedVia(EnumUtils.fromStringIgnoreCase(ApplicationChannel.class, rs.getString("created_via")));

        // ---------------- Disputant ----------------
        String disputantIdentityUUID = rs.getString("disputant_identity_uuid");
        DisputantType disputantType = BasicUtil.safeEnum(DisputantType.class, rs.getString("disputant_type"));
        String domainCode = rs.getString("disputant_domain_code");

        //todo: construct disputant from the above three and set disputant on didpute

        dispute.setNote(rs.getString("note"));

        // ---------------- Institution Codes ----------------
        dispute.setIssuerCode(rs.getString("issuer_code"));
        dispute.setAcquirerCode(rs.getString("acquirer_code"));
        dispute.setMerchantCode(rs.getString("merchant_code"));
        dispute.setBeneficiaryCode(rs.getString("beneficiary_code"));
        dispute.setSwitcherCode(rs.getString("switcher_code"));
        dispute.setBillerCode(rs.getString("biller_code"));
        dispute.setPlaintiffInstitutionCode(rs.getString("plaintiff_institution_code"));
        dispute.setDefendantInstitutionCode(rs.getString("defendant_institution_code"));
        dispute.setOnUsTransaction(rs.getBoolean("on_us_transaction"));

        // ---------------- Resolution Flags ----------------
        dispute.setFinalized(rs.getBoolean("is_finalized"));
        dispute.setResolved(rs.getBoolean("is_resolved"));
        dispute.setResolvedInCustomerFavor(rs.getBoolean("resolved_in_customer_favor"));

        return dispute;
    }

    public List<Dispute> mapFromJson(String json) {
        List<Dispute> disputes = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataArray = root.path("data");
            if (dataArray.isArray()) {
                for (JsonNode node : dataArray) {
                    Dispute d = new Dispute();

                    d.setId(node.path("id").asLong());
                    d.setLogCode(node.path("log_code").asText(null));

                    String createdAt = node.path("created_at").asText(null);
                    if (createdAt != null) d.setCreatedAt(LocalDateTime.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME));

                    String currentState = node.path("current_state").asText(null);
                    if (currentState != null) d.setCurrentState(EnumUtils.fromStringIgnoreCase(DisputeState.class, currentState));

                    String previousState = node.path("previous_state").asText(null);
                    if (previousState != null) d.setPreviousState(EnumUtils.fromStringIgnoreCase(DisputeState.class, previousState));

                    d.setDisputeMode(EnumUtils.fromStringIgnoreCase(DisputeMode.class, node.path("dispute_mode").asText(null)));
                    d.setLocked(node.path("locked").asBoolean(false));

                    // Transaction summary
                    d.setTransactionInstrument(EnumUtils.fromStringIgnoreCase(TransactionInstrument.class,
                            node.path("oti_instrument").asText(null)));
                    d.setTransactionPaymentRail(EnumUtils.fromStringIgnoreCase(PaymentRail.class,
                            node.path("oti_rail").asText(null)));

                    d.setTransactionAmount(node.path("transaction_amount").decimalValue());
                    String txDate = node.path("transaction_date").asText(null);
                    if (txDate != null) d.setTransactionDate(LocalDateTime.parse(txDate, DateTimeFormatter.ISO_DATE_TIME));

                    // Institutions & codes
                    d.setIssuerCode(node.path("issuer_code").asText(null));
                    d.setAcquirerCode(node.path("acquirer_code").asText(null));
                    d.setMerchantCode(node.path("merchant_code").asText(null));
                    d.setBeneficiaryCode(node.path("beneficiary_code").asText(null));
                    d.setSwitcherCode(node.path("switcher_code").asText(null));
                    d.setPlaintiffInstitutionCode(node.path("plaintiff_institution_code").asText(null));
                    d.setDefendantInstitutionCode(node.path("defendant_institution_code").asText(null));
                    d.setOnUsTransaction(node.path("on_us_transaction").asBoolean(false));

                    //disputant todo: please construct disputant with these three and set on dispute
                    String disputantIdentityUUID = node.path("disputant_identity_uuid").asText();
                    DisputantType disputantType = BasicUtil.safeEnum(DisputantType.class, node.path("disputant_type").asText());
                    String domainCode = node.path("disputant_domain_code").asText();

                    // Flags
                    d.setFinalized(node.path("is_finalized").asBoolean(false));
                    d.setResolved(node.path("is_resolved").asBoolean(false));
                    d.setResolvedInCustomerFavor(node.path("resolved_in_customer_favor").asBoolean(false));

                    // Optional fields
                    d.setNote(node.path("note").asText(null));
                    d.setCreatedVia(EnumUtils.fromStringIgnoreCase(ApplicationChannel.class, node.path("created_via").asText(null)));

                    disputes.add(d);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error mapping dispute JSON", e);
        }
        return disputes;
    }

}

/*
✅ How to use it:

1. Fetch all disputes created by a specific issuer:

SELECT fetch_dispute_dynamic_where(
    p_limit => 20,
    p_offset => 0,
    p_where_clause => 'd.issuer_code = ''ISSUER123'''
);


2. Fetch only unresolved and unlocked disputes:

SELECT fetch_dispute_dynamic_where(
    p_limit => 50,
    p_where_clause => 'd.is_resolved = false AND d.locked = false'
);


3. Fetch disputes by date range:

SELECT fetch_dispute_dynamic_where(
    p_limit => 50,
    p_where_clause => 'd.created_at >= ''2025-01-01'' AND d.created_at < ''2025-02-01'''
);

✅ Advantages of this approach:

Flexible: Pass any valid SQL conditions as p_where_clause.

Lightweight: Only summary info is fetched.

Safe dynamic building: Uses format() to safely build SQL.

Ready for dashboards or APIs: Returns JSONB with data + meta.
 */