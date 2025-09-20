package com.netstra.disputes.services.client;

import com.netstra.disputes.services.client.util.MtlsContextEntry;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MtlsContextRepository extends CrudRepository<MtlsContextEntry, String> {
    // String = domainCode (the @Id field)
}
