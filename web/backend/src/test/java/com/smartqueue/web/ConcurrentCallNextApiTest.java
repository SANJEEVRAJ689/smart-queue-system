package com.smartqueue.web;

import com.smartqueue.web.dto.CustomerDtos.BookTokenRequest;
import com.smartqueue.web.dto.StaffDtos.StaffTokenResponse;
import com.smartqueue.web.entity.CounterEntity;
import com.smartqueue.web.repository.CounterRepository;
import com.smartqueue.web.repository.TokenRepository;
import com.smartqueue.web.service.CustomerService;
import com.smartqueue.web.service.StaffService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ConcurrentCallNextApiTest {

    @Autowired
    private StaffService staffService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CounterRepository counterRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Test
    @DisplayName("Concurrent callNext on the same counter produces only one claimed token")
    void testConcurrentCallNextOneActive() throws Exception {
        int counterId = 1;

        // Reset counter 1 to OPEN
        CounterEntity counter = counterRepository.findById(counterId).orElseThrow();
        counter.setStatus("OPEN");
        counter.setCurrentTokenId(null);
        counterRepository.save(counter);

        // Seed 3 waiting tokens
        for (int i = 1; i <= 3; i++) {
            BookTokenRequest req = new BookTokenRequest();
            req.setServiceId(1);
            req.setCustomerName("Concurrent Guest " + i);
            req.setPhone("910000000" + i);
            customerService.bookToken(req, "127.0.0.1");
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(2);
        List<Optional<StaffTokenResponse>> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    results.add(staffService.callNextToken(counterId, null));
                } catch (Exception ignored) {
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(2, results.size());
        assertTrue(results.get(0).isPresent());
        assertTrue(results.get(1).isPresent());

        // Both calls must observe the exact same token ID (double-call safe)
        assertEquals(results.get(0).get().getId(), results.get(1).get().getId());

        // Clean up: start serving and complete the called token
        int activeId = results.get(0).get().getId();
        staffService.startServing(activeId, null);
        staffService.completeToken(activeId, counterId, null);
    }
}
