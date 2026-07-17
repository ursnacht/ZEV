package ch.nacht.service;

import ch.nacht.exception.NoOrganizationException;

import jakarta.persistence.EntityManager;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Pinnt die Fail-closed-Invariante des Mandantenfilters (OWASP A01): Kann der orgFilter
 * nicht aktiviert werden (kein Kontext, Session-Fehler), muss eine Exception fliegen –
 * eine Query darf niemals still ohne Mandantenfilter laufen (Cross-Tenant-Leak).
 */
@ExtendWith(MockitoExtension.class)
public class HibernateFilterServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private Session session;

    @Mock
    private Filter filter;

    private HibernateFilterService hibernateFilterService;

    @BeforeEach
    void setUp() {
        // Kein @InjectMocks: org.hibernate.Session erweitert EntityManager, die Injektion
        // per Typ wäre mehrdeutig (session könnte als EntityManager injiziert werden).
        hibernateFilterService = new HibernateFilterService(entityManager, organizationContextService);
    }

    @Test
    void enableOrgFilter_MitOrgId_AktiviertFilter() {
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.getEnabledFilter("orgFilter")).thenReturn(null);
        when(session.enableFilter("orgFilter")).thenReturn(filter);

        assertDoesNotThrow(() -> hibernateFilterService.enableOrgFilter(42L));

        verify(filter).setParameter("orgId", 42L);
    }

    @Test
    void enableOrgFilter_BereitsAktiv_AktualisiertParameter() {
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.getEnabledFilter("orgFilter")).thenReturn(filter);

        hibernateFilterService.enableOrgFilter(7L);

        verify(session, never()).enableFilter(any());
        verify(filter).setParameter("orgId", 7L);
    }

    @Test
    void enableOrgFilter_OhneKontext_FailClosed() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(null);

        assertThrows(NoOrganizationException.class, () -> hibernateFilterService.enableOrgFilter());
        verifyNoInteractions(entityManager);
    }

    @Test
    void enableOrgFilter_NullOrgId_FailClosed() {
        assertThrows(NoOrganizationException.class, () -> hibernateFilterService.enableOrgFilter(null));
        verifyNoInteractions(entityManager);
    }

    @Test
    void enableOrgFilter_SessionFehler_FailClosed() {
        when(entityManager.unwrap(Session.class)).thenThrow(new IllegalStateException("Session closed"));

        assertThrows(IllegalStateException.class, () -> hibernateFilterService.enableOrgFilter(42L));
    }

    @Test
    void enableOrgFilter_AusKontext_NutztAktuelleOrgId() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(99L);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.getEnabledFilter("orgFilter")).thenReturn(filter);

        hibernateFilterService.enableOrgFilter();

        verify(filter).setParameter("orgId", 99L);
    }
}
