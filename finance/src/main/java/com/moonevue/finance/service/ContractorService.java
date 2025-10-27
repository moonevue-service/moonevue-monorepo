package com.moonevue.finance.service;

import com.moonevue.finance.dto.contractor.ContractorRequest;
import com.moonevue.finance.dto.contractor.ContractorResponse;
import com.moonevue.finance.exception.ConflictException;
import com.moonevue.finance.exception.NotFoundException;
import com.moonevue.core.entity.Contractor;
import com.moonevue.core.repository.ContractorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContractorService {

    private final ContractorRepository contractorRepository;

    @Transactional
    public ContractorResponse create(ContractorRequest req) {
        if (contractorRepository.existsByCpfCnpj(req.cpfCnpj())) {
            throw new ConflictException("Contractor with CPF/CNPJ already exists");
        }
        Contractor c = new Contractor();
        c.setName(req.name());
        c.setBusinessName(req.businessName());
        c.setPersonType(req.personType());
        c.setCpfCnpj(req.cpfCnpj());
        c.setContactEmail(req.contactEmail());
        c.setPhone(req.phone());
        contractorRepository.save(c);
        return ContractorResponse.from(c);
    }

    @Transactional(readOnly = true)
    public Contractor getEntity(Long contractorId) {
        return contractorRepository.findById(contractorId)
                .orElseThrow(() -> new NotFoundException("Contractor not found: " + contractorId));
    }

    @Transactional
    public ContractorResponse update(Long contractorId, ContractorRequest req) {
        Contractor c = getEntity(contractorId);
        c.setName(req.name());
        c.setBusinessName(req.businessName());
        c.setPersonType(req.personType());
        c.setContactEmail(req.contactEmail());
        c.setPhone(req.phone());
        return ContractorResponse.from(c);
    }

    @Transactional
    public void delete(Long contractorId) {
        if (!contractorRepository.existsById(contractorId)) {
            throw new NotFoundException("Contractor not found: " + contractorId);
        }
        contractorRepository.deleteById(contractorId);
    }
}
