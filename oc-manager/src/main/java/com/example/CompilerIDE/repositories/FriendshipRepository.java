package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Friendship;
import com.example.CompilerIDE.providers.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Integer> {
    List<Friendship> findByClient1OrClient2(Client client1, Client client2);
    Optional<Friendship> findByClient1AndClient2(Client client1, Client client2);
}
