package com.example.appbackend.repository;

import com.example.appbackend.entity.MaxKbAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface MaxKbAccountRepository extends JpaRepository<MaxKbAccount, Long>, JpaSpecificationExecutor<MaxKbAccount> {
}
