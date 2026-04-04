package com.ohchurus.budget.karate;

import com.intuit.karate.junit5.Karate;

class KarateRunnerTest {

    @Karate.Test
    Karate testAll() {
        return Karate.run().relativeTo(getClass());
    }
}
