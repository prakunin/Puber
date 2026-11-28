CODEX: ran: review, adversarial
CONFIRMED:
(none)
REJECTED:
- [medium] Low-RAM upgrade path is unverified (evidence doc lines 8-14). The device was not low-RAM, so the new tests don't cover that opening path. This is a verification gap, not a defect. Codex itself says it is "not an observed production failure" and gives no input or state that leaves the writer in rollback mode. The explicit `setJournalMode(WRITE_AHEAD_LOGGING)` replaces the AUTOMATIC choice. `productionBuilderOpensTheFileInWalWithNormalSync` asserts WAL and synchronous NORMAL on the production builder's writer connection. `productionBuilderSwitchesARollbackJournalFileToWal` checks the file header bytes after a rollback-to-WAL switch. Neither depends on the device being low-RAM. Rejected as `unproven`.
- The plain review found nothing. It only noted that the behaviour on low-RAM hardware is unverified, which is the same point as above.
VERDICT: clean
