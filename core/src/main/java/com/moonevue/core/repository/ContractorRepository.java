package com.moonevue.core.repository;

import com.moonevue.core.entity.Contractor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContractorRepository extends JpaRepository<Contractor, Long> {

    Optional<Contractor> findByCpfCnpj(String cpfCnpj);

    boolean existsByCpfCnpj(String cpfCnpj);

    Page<Contractor> findByNameContainingIgnoreCase(String name, Pageable pageable);
}