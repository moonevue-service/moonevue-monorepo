package com.moonevue.finance.analytics.service;

import com.moonevue.finance.analytics.domain.InsightSeverity;
import com.moonevue.finance.analytics.dto.ClientRevenueResponse;
import com.moonevue.finance.analytics.dto.ExecutiveSummaryResponse;
import com.moonevue.finance.analytics.dto.InsightResponse;
import com.moonevue.finance.analytics.dto.ReceivablesResponse;
import com.moonevue.finance.analytics.dto.StatusBreakdownResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor de insights automáticos baseado em regras de negócio sobre os agregados.
 * As regras são determinísticas e explicáveis (sem ML nesta fase).
 */
@Component
public class InsightEngine {

    private static final BigDecimal CONCENTRATION_THRESHOLD = BigDecimal.valueOf(30);
    private static final BigDecimal OVERDUE_WARNING = BigDecimal.valueOf(10);
    private static final BigDecimal OVERDUE_CRITICAL = BigDecimal.valueOf(25);

    public List<InsightResponse> generate(ExecutiveSummaryResponse summary,
                                          List<ClientRevenueResponse> topClients,
                                          ReceivablesResponse receivables,
                                          List<StatusBreakdownResponse> statusBreakdown) {
        List<InsightResponse> insights = new ArrayList<>();

        // 1. Crescimento de receita vs período anterior
        if (summary != null && summary.growth() != null) {
            BigDecimal growth = summary.growth().grossRevenuePct();
            if (growth != null && growth.signum() > 0) {
                insights.add(new InsightResponse(InsightSeverity.POSITIVE, "Receita",
                        "Crescimento de receita",
                        "A receita aumentou " + growth.stripTrailingZeros().toPlainString()
                                + "% em relação ao período anterior.",
                        growth));
            } else if (growth != null && growth.signum() < 0) {
                insights.add(new InsightResponse(InsightSeverity.WARNING, "Receita",
                        "Queda de receita",
                        "A receita caiu " + growth.abs().stripTrailingZeros().toPlainString()
                                + "% em relação ao período anterior.",
                        growth));
            }
        }

        // 2. Risco de concentração de clientes
        if (topClients != null && !topClients.isEmpty()) {
            ClientRevenueResponse top = topClients.get(0);
            if (top.sharePct() != null && top.sharePct().compareTo(CONCENTRATION_THRESHOLD) >= 0) {
                insights.add(new InsightResponse(InsightSeverity.WARNING, "Comercial",
                        "Concentração de receita",
                        "O cliente " + top.clientName() + " representa "
                                + top.sharePct().stripTrailingZeros().toPlainString()
                                + "% da receita e apresenta risco de concentração.",
                        top.sharePct()));
            }
        }

        // 3. Inadimplência
        if (receivables != null && receivables.overdueRatioPct() != null) {
            BigDecimal overdue = receivables.overdueRatioPct();
            if (overdue.compareTo(OVERDUE_CRITICAL) >= 0) {
                insights.add(new InsightResponse(InsightSeverity.CRITICAL, "Financeiro",
                        "Inadimplência elevada",
                        "A inadimplência está em " + overdue.stripTrailingZeros().toPlainString()
                                + "% dos recebíveis e exige ação imediata.",
                        overdue));
            } else if (overdue.compareTo(OVERDUE_WARNING) >= 0) {
                insights.add(new InsightResponse(InsightSeverity.WARNING, "Financeiro",
                        "Inadimplência em atenção",
                        "A inadimplência está em " + overdue.stripTrailingZeros().toPlainString()
                                + "% dos recebíveis.",
                        overdue));
            }
        }

        // 4. Taxa de conversão (informativo)
        if (summary != null && summary.conversionRatePct() != null
                && summary.totalTransactions() > 0) {
            insights.add(new InsightResponse(InsightSeverity.INFO, "Operacional",
                    "Taxa de conversão",
                    "Taxa de conversão de pagamentos em "
                            + summary.conversionRatePct().stripTrailingZeros().toPlainString() + "%.",
                    summary.conversionRatePct()));
        }

        return insights;
    }
}
