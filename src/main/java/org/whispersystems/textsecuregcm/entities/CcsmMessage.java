package org.whispersystems.textsecuregcm.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;

public class CcsmMessage {
  @JsonProperty
  @NotEmpty
  private String ccsmId;

  @JsonProperty
  @NotEmpty
  private String number;

  @JsonProperty
  @Min(1)
  private int deviceId;

  @JsonProperty
  private String message;

  @JsonProperty
  private boolean receipt;

  @JsonProperty
  private boolean notification;

  public CcsmMessage() {}

  public CcsmMessage(String ccsmId, String number, int deviceId, String message, boolean receipt, boolean notification) {
    this.ccsmId        = ccsmId;
    this.number       = number;
    this.deviceId     = deviceId;
    this.message      = message;
    this.receipt      = receipt;
    this.notification = notification;
  }
}
