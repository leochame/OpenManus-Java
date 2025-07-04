package com.openmanus.java.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a sequence of messages, corresponding to app/schema.py's Memory.
 */
public class Memory {

    private List<Message> messages = new ArrayList<>();
    private int maxMessages = 100;
    
    // Getters and Setters
    public List<Message> getMessages() {
        return messages;
    }
    
    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }
    
    public int getMaxMessages() {
        return maxMessages;
    }
    
    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public void addMessage(Message message) {
        this.messages.add(message);
        enforceMessageLimit();
    }

    public void addMessages(List<Message> messages) {
        this.messages.addAll(messages);
        enforceMessageLimit();
    }

    public void clear() {
        this.messages.clear();
    }

    public List<Message> getRecentMessages(int n) {
        if (n <= 0) {
            return new ArrayList<>();
        }
        int start = Math.max(0, this.messages.size() - n);
        return new ArrayList<>(this.messages.subList(start, this.messages.size()));
    }

    private void enforceMessageLimit() {
        if (this.messages.size() > this.maxMessages) {
            int toRemove = this.messages.size() - this.maxMessages;
            this.messages = new ArrayList<>(this.messages.subList(toRemove, this.messages.size()));
        }
    }
}