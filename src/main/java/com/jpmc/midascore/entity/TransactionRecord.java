package com.jpmc.midascore.entity;

import jakarta.persistence.*;

@Entity
public class TransactionRecord {
    @Id
    @GeneratedValue
    private long id;

    @Column(nullable = false)
    private long senderId;

    @Column(nullable = false)
    private long recipientId;

    @Column(nullable = false)
    private float amount;

    public TransactionRecord() {}

    public TransactionRecord(long senderId, long recipientId, float amount) {
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.amount = amount;
    }

    public long getId() { return id; }
    public long getSenderId() { return senderId; }
    public long getRecipientId() { return recipientId; }
    public float getAmount() { return amount; }
}