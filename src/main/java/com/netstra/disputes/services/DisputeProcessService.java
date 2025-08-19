package com.netstra.disputes.services;

import com.netstra.disputes.dao.DisputeDao;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DisputeProcessService {
    private final DisputeDao disputeDao;

    public void processNonFinalizeDisputes(){
        //if expired, escalate async
        //if required more enchrichment: silently call for enchrichment async
    }
}
