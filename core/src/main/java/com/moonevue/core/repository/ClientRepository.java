package com.moonevue.core.repository;

import com.moonevue.core.entity.Client;
import com.moonevue.core.enums.ClientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByContractorIdAndCpfCnpj(Long contractorId, String cpfCnpj);

    boolean existsByContractorIdAndCpfCnpj(Long contractorId, String cpfCnpj);

    Page<Client> findByContractorId(Long contractorId, Pageable pageable);

    Page<Client> findByContractorIdAndStatus(Long contractorId, ClientStatus status, Pageable pageable);

    @Query("""
           select c from Client c
           where c.contractor.id = :contractorId
             and (upper(c.name) like upper(concat('%', :q, '%'))
               or upper(c.email) like upper(concat('%', :q, '%'))
               or c.cpfCnpj like concat('%', :q, '%'))
           """)
    Page<Client> search(Long contractorId, String q, Pageable pageable);

    List<Client> findByContractorIdAndEmailIn(Long contractorId, Collection<String> emails);
}
