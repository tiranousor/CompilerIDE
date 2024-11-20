package com.example.CompilerIDE.services;

import com.example.CompilerIDE.providers.FriendRequest;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.repositories.FriendRequestRepository;
import com.example.CompilerIDE.repositories.FriendshipRepository;
import com.example.CompilerIDE.providers.Friendship;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Service
public class FriendRequestService {

    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;

    @Autowired
    public FriendRequestService(FriendRequestRepository friendRequestRepository, FriendshipRepository friendshipRepository) {
        this.friendRequestRepository = friendRequestRepository;
        this.friendshipRepository = friendshipRepository;
    }

    public void sendFriendRequest(Client sender, Client receiver) throws Exception {
        if (sender.equals(receiver)) {
            throw new Exception("You cannot send a friend request to yourself.");
        }

        Optional<FriendRequest> existingRequest = friendRequestRepository.findBySenderAndReceiver(sender, receiver);
        if (existingRequest.isPresent()) {
            throw new Exception("Friend request already sent.");
        }

        Optional<Friendship> existingFriendship = friendshipRepository.findByClient1AndClient2(sender, receiver);
        if (existingFriendship.isPresent()) {
            throw new Exception("You are already friends.");
        }

        FriendRequest friendRequest = new FriendRequest();
        friendRequest.setSender(sender);
        friendRequest.setReceiver(receiver);
        friendRequest.setStatus(FriendRequest.RequestStatus.PENDING);
        friendRequest.setTimestamp(new Timestamp(System.currentTimeMillis()));
        friendRequestRepository.save(friendRequest);
    }

    public void acceptFriendRequest(Integer requestId, Client receiver) throws Exception {
        FriendRequest friendRequest = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new Exception("Friend request not found."));

        if (!friendRequest.getReceiver().equals(receiver)) {
            throw new Exception("You are not authorized to accept this request.");
        }

        friendRequest.setStatus(FriendRequest.RequestStatus.ACCEPTED);
        friendRequestRepository.save(friendRequest);

        Client sender = friendRequest.getSender();
        Client receiverClient = friendRequest.getReceiver();

        Friendship friendship = new Friendship();
        if (sender.getId() < receiverClient.getId()) {
            friendship.setClient1(sender);
            friendship.setClient2(receiverClient);
        } else {
            friendship.setClient1(receiverClient);
            friendship.setClient2(sender);
        }
        friendship.setSince(new Timestamp(System.currentTimeMillis()));
        friendshipRepository.save(friendship);
    }

    public void rejectFriendRequest(Integer requestId, Client receiver) throws Exception {
        FriendRequest friendRequest = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new Exception("Friend request not found."));

        if (!friendRequest.getReceiver().equals(receiver)) {
            throw new Exception("You are not authorized to reject this request.");
        }

        friendRequest.setStatus(FriendRequest.RequestStatus.REJECTED);
        friendRequestRepository.save(friendRequest);
    }

    public List<FriendRequest> getPendingReceivedRequests(Client receiver) {
        return friendRequestRepository.findByReceiverAndStatus(receiver, FriendRequest.RequestStatus.PENDING);
    }

    public List<FriendRequest> getPendingSentRequests(Client sender) {
        return friendRequestRepository.findBySenderAndStatus(sender, FriendRequest.RequestStatus.PENDING);
    }
}
