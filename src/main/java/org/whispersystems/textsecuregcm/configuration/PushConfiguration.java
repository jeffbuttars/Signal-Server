package org.whispersystems.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;

public class PushConfiguration {
  @JsonProperty
  @NotEmpty
  private String host;

  @JsonProperty
  @Min(1)
  private int port;

  @JsonProperty
  @NotEmpty
  private String username;

  @JsonProperty
  @NotEmpty
  private String password;

  @JsonProperty
  @Min(0)
  private int queueSize = 200;

  @JsonProperty
  @NotEmpty
  private String ccsmHost;

  @JsonProperty
  @NotEmpty
  private String ccsmPath;

  @JsonProperty
  @NotEmpty
  private String ccsmToken;

  @JsonProperty
  @Min(1)
  private int ccsmPort;

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public String getCcsmHost() {
    return ccsmHost;
  }

  public int getCcsmPort() {
      return ccsmPort;
  }

  public int getQueueSize() {
    return queueSize;
  }

  public String getCcsmPath() {
      return ccsmPath;
  }

  public String getCcsmToken() {
      return ccsmToken;
  }
}
