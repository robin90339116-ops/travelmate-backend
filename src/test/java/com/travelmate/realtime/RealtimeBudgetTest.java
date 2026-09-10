package com.travelmate.realtime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RealtimeBudgetTest {
 @Test void capsExplicitContinuationAtSevenWithoutResettingHistory(){var repo=mock(RealtimeBudgetRepository.class);when(repo.reserve("acceptance-v1",7)).thenReturn(1);new RealtimeBudget(repo,100).reserve();verify(repo).reserve("acceptance-v1",7);}
 @Test void failsClosedWhenAllowanceExhausted(){var repo=mock(RealtimeBudgetRepository.class);assertThrows(com.travelmate.common.ApiException.class,()->new RealtimeBudget(repo,5).reserve());}
 @Test void reportsPersistedRemaining(){var repo=mock(RealtimeBudgetRepository.class);var row=new RealtimeBudgetRecord();row.setReservations(3);when(repo.findById("acceptance-v1")).thenReturn(java.util.Optional.of(row));assertEquals(2,new RealtimeBudget(repo,5).remaining());}
}
