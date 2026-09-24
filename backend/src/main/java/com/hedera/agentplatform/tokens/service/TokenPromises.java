package com.hedera.agentplatform.tokens.service;

import com.hedera.agentplatform.tokens.dto.TokenViews.Promise;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.TokenKeys;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a token's keys into what it promises, in plain words. Used for the Studio preview (the keys
 * the token will be created with) and for the Passport (the keys the Mirror Node shows), so a
 * promise is never written by hand: it is read from the keys.
 */
final class TokenPromises {

  static final String GUARANTEE = "GUARANTEE";
  static final String POWER = "POWER";

  private TokenPromises() {}

  /**
   * @param ourKey the key this platform signs with; null when unknown
   * @param planned true for a token not created yet (the Studio preview)
   */
  static List<Promise> of(
      TokenKeys keys,
      String supplyType,
      String totalSupply,
      String maxSupply,
      String symbol,
      int customFees,
      String pauseStatus,
      String ourKey,
      boolean planned) {
    List<Promise> promises = new ArrayList<>();

    if (keys.admin() == null) {
      promises.add(g("Nobody can change or delete it",
          "It has no admin key, so its keys and rules are final: the promises below hold forever."));
    } else {
      promises.add(p("Its admin can change or delete it",
          "It has an admin key: its keys and settings can be updated, so the promises below can change."));
    }

    if (keys.supply() == null) {
      promises.add(g("Supply fixed forever at " + totalSupply + " " + symbol,
          "It has no supply key: no one can ever mint more."));
    } else {
      String holder =
          planned ? "This platform will hold the supply key."
              : ourKey != null && ourKey.equalsIgnoreCase(keys.supply()) ? "This platform holds the supply key."
              : "The supply key belongs to someone else, not this platform.";
      if ("FINITE".equals(supplyType) && maxSupply != null) {
        promises.add(p("Never more than " + maxSupply + " " + symbol,
            "More can be minted, never beyond this cap. " + holder));
      } else {
        promises.add(p("More can be minted, with no cap", holder));
      }
    }

    promises.add(keys.freeze() == null
        ? g("Nobody can freeze a holder", "It has no freeze key: holders can always move their tokens.")
        : p("Holders can be frozen", "Its freeze key can stop an account from moving it."));

    promises.add(keys.wipe() == null
        ? g("Nobody can take tokens back", "It has no wipe key: what someone holds stays theirs.")
        : p("Tokens can be wiped from holders", "Its wipe key can remove tokens from an account."));

    promises.add(keys.kyc() == null
        ? g("No KYC gate", "Any account that associates with it can hold it.")
        : p("Holders need KYC approval", "Its KYC key decides which accounts may hold it."));

    if (keys.pause() == null) {
      promises.add(g("It can never be paused", "It has no pause key: transfers can always happen."));
    } else {
      promises.add(p("Transfers can be paused",
          "Its pause key can stop every transfer." + ("PAUSED".equals(pauseStatus) ? " It is PAUSED now." : "")));
    }

    if (customFees > 0) {
      promises.add(p("Transfers carry " + customFees + " custom fee" + (customFees > 1 ? "s" : ""),
          "Part of each transfer is collected as a fee."));
    } else if (keys.feeSchedule() == null) {
      promises.add(g("No transfer fees, ever", "It has no fees and no fee schedule key to add some."));
    } else {
      promises.add(p("Transfer fees could be added", "It has none today, but its fee schedule key can add some."));
    }
    return promises;
  }

  private static Promise g(String title, String detail) {
    return new Promise(GUARANTEE, title, detail);
  }

  private static Promise p(String title, String detail) {
    return new Promise(POWER, title, detail);
  }
}
