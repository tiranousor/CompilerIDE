package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.FriendRequest;
import com.example.CompilerIDE.providers.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Integer> {
    Optional<FriendRequest> findBySenderAndReceiver(Client sender, Client receiver);
    List<FriendRequest> findByReceiverAndStatus(Client receiver, FriendRequest.RequestStatus status);
    List<FriendRequest> findBySenderAndStatus(Client sender, FriendRequest.RequestStatus status);
}
