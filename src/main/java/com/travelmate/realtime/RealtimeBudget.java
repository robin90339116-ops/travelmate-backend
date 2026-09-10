package com.travelmate.realtime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import com.travelmate.common.ApiException;

@Entity @Getter @Setter @Table(name="realtime_budget")
class RealtimeBudgetRecord {
 @Id private String id;
 private int reservations;
}
interface RealtimeBudgetRepository extends JpaRepository<RealtimeBudgetRecord,String> {
 @Modifying @Transactional
  @org.springframework.data.jpa.repository.Query("update RealtimeBudgetRecord b set b.reservations=b.reservations+1 where b.id=:id and b.reservations<:cap")
 int reserve(@Param("id") String id,@Param("cap") int cap);
}
/** Persistent session allowance, not a provider billing ledger. Failed dials consume an allowance. */
@Service
public class RealtimeBudget {
 private static final String ID="acceptance-v1";
 private final RealtimeBudgetRepository repository;
 private final int cap;
 public RealtimeBudget(RealtimeBudgetRepository repository,@Value("${app.realtime.max-sessions:5}") int cap){this.repository=repository;this.cap=Math.max(0,Math.min(cap,7));}
 @jakarta.annotation.PostConstruct public void initialize(){
  if(!repository.existsById(ID)){var record=new RealtimeBudgetRecord();record.setId(ID);try{repository.saveAndFlush(record);}catch(org.springframework.dao.DataIntegrityViolationException ignored){}}
 }
 public void reserve(){if(repository.reserve(ID,cap)!=1)throw ApiException.serviceUnavailable("实时测试会话额度已用完，请核对账单后再调整额度");}
 public int remaining(){return Math.max(0,cap-repository.findById(ID).map(RealtimeBudgetRecord::getReservations).orElse(0));}
}
