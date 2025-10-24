package utilities;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class OCREvidenceData {
    private String transactionRef;
    private BigDecimal amount;
    private LocalDateTime transactionDate;
    private String channel;
}

