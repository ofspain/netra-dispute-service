package com.netstra.disputes.services.client;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EndpointSecretRepository extends CrudRepository<EndpointSecret, String> {
}
