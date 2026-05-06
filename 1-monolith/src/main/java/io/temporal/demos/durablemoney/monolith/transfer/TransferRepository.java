package io.temporal.demos.durablemoney.monolith.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

interface TransferRepository extends JpaRepository<Transfer, UUID> {}
