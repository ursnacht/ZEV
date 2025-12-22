package ch.nacht.service;

import ch.nacht.entity.Einheit;
import ch.nacht.repository.EinheitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class EinheitService {

    private final EinheitRepository einheitRepository;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    public EinheitService(EinheitRepository einheitRepository,
                          OrganizationContextService organizationContextService,
                          HibernateFilterService hibernateFilterService) {
        this.einheitRepository = einheitRepository;
        this.organizationContextService = organizationContextService;
        this.hibernateFilterService = hibernateFilterService;
    }

    @Transactional(readOnly = true)
    public List<Einheit> getAllEinheiten() {
        hibernateFilterService.enableOrgFilter();
        return einheitRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Optional<Einheit> getEinheitById(Long id) {
        hibernateFilterService.enableOrgFilter();
        return einheitRepository.findById(id);
    }

    @Transactional
    public Einheit createEinheit(Einheit einheit) {
        hibernateFilterService.enableOrgFilter();
        einheit.setOrgId(organizationContextService.getCurrentOrgId());
        return einheitRepository.save(einheit);
    }

    @Transactional
    public Optional<Einheit> updateEinheit(Long id, Einheit einheit) {
        hibernateFilterService.enableOrgFilter();
        Optional<Einheit> existingEinheit = einheitRepository.findById(id);
        if (existingEinheit.isEmpty()) {
            return Optional.empty();
        }
        einheit.setId(id);
        einheit.setOrgId(existingEinheit.get().getOrgId());
        return Optional.of(einheitRepository.save(einheit));
    }

    @Transactional
    public boolean deleteEinheit(Long id) {
        hibernateFilterService.enableOrgFilter();
        if (!einheitRepository.existsById(id)) {
            return false;
        }
        einheitRepository.deleteById(id);
        return true;
    }
}
