package vn.edu.vlu.trippocket.data

/**
 * Plain Kotlin (no Android imports) so it can be unit-tested on the JVM.
 * Rules come from section 3.4 of the proposal.
 */
object ExpenseCalculator {

    /**
     * Equal split in whole dong. Leftover dong go one each to the first
     * participants in member order: 100,001 for 3 people -> 33,334 / 33,334 / 33,333.
     */
    fun splitEqually(amount: Long, participants: List<TripMember>): Map<String, Long> {
        require(participants.isNotEmpty()) { "An expense needs at least one participant" }
        val ordered = participants.sortedBy { it.orderIndex }
        val base = amount / ordered.size
        val remainder = amount % ordered.size
        return ordered.mapIndexed { index, member ->
            member.memberId to base + if (index < remainder) 1 else 0
        }.toMap()
    }

    /** balance = amount paid - own share. The balances always sum to 0. */
    fun balances(members: List<TripMember>, expenses: List<Expense>): Map<String, Long> {
        val result = members.associate { it.memberId to 0L }.toMutableMap()
        for (expense in expenses) {
            result[expense.payerId] = (result[expense.payerId] ?: 0L) + expense.amount
            for ((memberId, share) in expense.shares) {
                result[memberId] = (result[memberId] ?: 0L) - share
            }
        }
        return result
    }

    fun paidBy(memberId: String, expenses: List<Expense>): Long =
        expenses.filter { it.payerId == memberId }.sumOf { it.amount }

    fun shareOf(memberId: String, expenses: List<Expense>): Long =
        expenses.sumOf { it.shares[memberId] ?: 0L }

    /**
     * Greedy settlement: repeatedly match the member who owes the most with the
     * member who is owed the most and transfer the smaller of the two amounts.
     * At most n - 1 payments; not claimed to be the global minimum.
     */
    fun settle(balances: Map<String, Long>): List<Settlement> {
        val debtors = balances.filterValues { it < 0 }.mapValues { -it.value }.toMutableMap()
        val creditors = balances.filterValues { it > 0 }.toMutableMap()
        val payments = mutableListOf<Settlement>()
        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            val (debtor, owes) = debtors.maxBy { it.value }
            val (creditor, owed) = creditors.maxBy { it.value }
            val amount = minOf(owes, owed)
            payments += Settlement(debtor, creditor, amount)
            if (owes == amount) debtors.remove(debtor) else debtors[debtor] = owes - amount
            if (owed == amount) creditors.remove(creditor) else creditors[creditor] = owed - amount
        }
        return payments
    }
}
