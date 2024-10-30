package com.example.CompilerIDE.services;

import com.example.CompilerIDE.providers.Friendship;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.repositories.FriendshipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
        List<Client> friends = new ArrayList<>();
        for (Friendship friendship : friendships) {
            if (friendship.getClient1().equals(client)) {
                friends.add(friendship.getClient2());
            } else {
                friends.add(friendship.getClient1());
            }
        }
        return friends;
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
