package com.jpmc.midascore.component;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.repository.TransactionRepository;
import com.jpmc.midascore.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaConsumer {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumer.class);

    private long senderId;
    private long recipientId;
    private float amount;

    private boolean validSenderId;
    private boolean validRecipientId;
    private boolean validAmount;

    private final UserRepository userRepository;
    private UserRecord sender;
    private UserRecord recipient;
    private float senderBalance;
    private float recipientBalance;

    private final TransactionRepository transactionRepository;

    @Autowired
    public KafkaConsumer(UserRepository userRepository, TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }


    @KafkaListener(topics = "${general.kafka-topic}", groupId = "midas-group")
    public void listen(Transaction transaction) {
        // logger.info("Received transaction: {}", transaction);
        senderId = transaction.getSenderId();
        recipientId = transaction.getRecipientId();
        amount = transaction.getAmount();

        sender = userRepository.findById(senderId);
        recipient = userRepository.findById(recipientId);
        senderBalance = sender.getBalance();
        recipientBalance = recipient.getBalance();

        validSenderId = (sender != null);
        validRecipientId = (recipient != null);
        if (senderBalance >= amount && amount >= 0) {
            validAmount = true;
        } else {
            validAmount = false;
        }
        
        

        if (validSenderId && validRecipientId && validAmount) {
            // Record Transactions
            TransactionRecord transactionRecord = new TransactionRecord(
                senderId,
                recipientId,
                amount
            );
            transactionRepository.save(transactionRecord);

            // Update Users
            recipientBalance += amount;
            recipient.setBalance(recipientBalance);
            senderBalance -= amount;
            sender.setBalance(senderBalance);

            // Record Users
            userRepository.save(sender); 
            userRepository.save(recipient);

            // Log
            logger.info("Valid transaction: {}", transaction);
            logger.info("Sender: {}", sender);
            logger.info("Recipient: {}", recipient);

        } else {
            logger.info("Invalid transaction: {}", transaction);
        }
        
    }
}
