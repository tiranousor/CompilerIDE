// UnbanRequestRepository.java
package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.UnbanRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnbanRequestRepository extends JpaRepository<UnbanRequest, Long> {
    UnbanRequest findByClient(Client client);
}
