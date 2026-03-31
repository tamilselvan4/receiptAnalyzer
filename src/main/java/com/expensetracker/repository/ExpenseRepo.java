package com.expensetracker.repository;

import com.expensetracker.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepo extends JpaRepository<Expense, Long> {
    List<Expense> getByUserId(long userId);

    @Query("""
        select distinct expense
        from Expense expense
        left join fetch expense.lineItems
        where expense.id = :expenseId and expense.user.id = :userId
        """)
    Optional<Expense> findDetailedByIdAndUserId(@Param("expenseId") Long expenseId, @Param("userId") long userId);
}
