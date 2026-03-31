package com.expensetracker.service;

import com.expensetracker.model.Expense;
import com.expensetracker.repository.ExpenseRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ExpenseService {

    private final ExpenseRepo expenseRepo;

    public ExpenseService(ExpenseRepo expenseRepo) {
        this.expenseRepo = expenseRepo;
    }

    @Transactional
    public Expense saveExpense(Expense expense) {
        expense.setLineItems(expense.getLineItems() == null ? List.of() : new ArrayList<>(expense.getLineItems()));
        return expenseRepo.save(expense);
    }

    @Transactional(readOnly = true)
    public List<Expense> getExpenseByUserId(long userId) {
        return expenseRepo.getByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Optional<Expense> getExpenseWithLineItems(long expenseId, long userId) {
        return expenseRepo.findDetailedByIdAndUserId(expenseId, userId);
    }
}
