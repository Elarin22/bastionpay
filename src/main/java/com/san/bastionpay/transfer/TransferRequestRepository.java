package com.san.bastionpay.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransferRequestRepository extends JpaRepository<TransferRequest, Long> {

    Optional<TransferRequest> findByMemberIdAndRequestId(Long memberId, String requestId);

    /** 사람이 확인해야 하는 이체(운영·관리자 화면용). */
    List<TransferRequest> findByStatusOrderByIdAsc(TransferStatus status);
}
