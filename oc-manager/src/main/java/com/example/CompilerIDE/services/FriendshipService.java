package com.example.CompilerIDE.services;

import com.example.CompilerIDE.providers.Friendship;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.repositories.FriendshipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;

    @Autowired
    public FriendshipService(FriendshipRepository friendshipRepository) {
        this.friendshipRepository = friendshipRepository;
    }

    public List<Client> getFriends(Client client) {
        List<Friendship> friendships = friendshipRepository.findByClient1OrClient2(client, client);
        List<Client> friends = friendships.stream()
                .map(f -> f.getClient1().equals(client) ? f.getClient2() : f.getClient1())
                .collect(Collectors.toList());
        return friends;
    }
    public boolean areFriends(Client client1, Client client2) {
        Optional<Friendship> friendshipOpt = friendshipRepository.findByClient1AndClient2(client1, client2);
        if (friendshipOpt.isPresent()) {
            return true;
        }
        // Также проверяем обратную связь
        friendshipOpt = friendshipRepository.findByClient1AndClient2(client2, client1);
        return friendshipOpt.isPresent();
    }
    public void removeFriendship(Integer friendshipId, Client client) throws Exception {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new Exception("Friendship not found."));

        if (!friendship.getClient1().equals(client) && !friendship.getClient2().equals(client)) {
            throw new Exception("You are not authorized to remove this friendship.");
        }

        friendshipRepository.delete(friendship);
    }
}
