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

import org.springframework.web.client.RestTemplate;
import com.jpmc.midascore.model.Incentive;

@Component
public class KafkaConsumer {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumer.class);

    private final RestTemplate restTemplate;
    private static final String INCENTIVE_API_URL = "http://localhost:8080/incentive";

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
    public KafkaConsumer(UserRepository userRepository, TransactionRepository transactionRepository, 
    RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.restTemplate = restTemplate;
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

            float incentiveAmount = 0;
            try {
                // Log the request
                logger.info("Sending to incentive API - Transaction: senderId={}, recipientId={}, amount={}", 
                    transaction.getSenderId(), 
                    transaction.getRecipientId(), 
                    transaction.getAmount()
                );

                // Call Incentive API
                Incentive incentive = restTemplate.postForObject(
                    INCENTIVE_API_URL,
                    transaction,
                    Incentive.class
                );
                incentiveAmount = incentive.getAmount();
            
                // Log the full response
                logger.info("Raw incentive response: {}", incentive);
                logger.info("Incentive amount: {}", incentiveAmount);
                logger.info("Incentive toString: {}", incentive.toString());
            } catch (Exception e) {
                logger.error("Error calling incentive API: {}", e.getMessage());
            }
            

            // Update Users
            recipientBalance += amount + incentiveAmount;
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
