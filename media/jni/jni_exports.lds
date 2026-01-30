{
  # Only export the primary JNI loading entrypoint.
  global:
    JNI_OnLoad;

  # Hide everything else.
  local:
    *;
};
