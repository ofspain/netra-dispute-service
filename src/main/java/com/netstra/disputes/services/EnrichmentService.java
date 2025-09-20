package com.netstra.disputes.services;

import com.netra.commons.models.Dispute;
import com.netra.commons.models.FinancialInstitution;
import com.netra.commons.models.Transaction;
import com.netra.commons.models.endpoint.EndpointConfig;

public class EnrichmentService {

    public void enrichWithIssuerData(Dispute dispute){
        Transaction transaction = dispute.getTransaction();
        FinancialInstitution issuer = transaction.getIssuer();
        if(!issuer.isEnabled()){
            //do something nasty
        }
        EndpointConfig config = issuer.getEndpointConfig();

    }
}
