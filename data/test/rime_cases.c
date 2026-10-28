/*
 * Runs Lychee's Rime schemas on the host librime and checks candidates.
 *
 *   rime_cases <shared_data_dir> <user_data_dir> cases.txt     check every case
 *   rime_cases <shared_data_dir> <user_data_dir> -d schema input [option=0|1 ...]
 *                                                              dump candidates
 *
 * cases.txt: one case per line, tab-separated:
 *   schema  input  options(- or a=1,b=0)  expected_text  within_top_n  [comment_substring]
 */
#include <rime_api.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static RimeApi *rime;

static void apply_options(RimeSessionId s, char *opts) {
  if (!opts || strcmp(opts, "-") == 0) return;
  for (char *tok = strtok(opts, ","); tok; tok = strtok(NULL, ",")) {
    char *eq = strchr(tok, '=');
    if (!eq) continue;
    *eq = 0;
    rime->set_option(s, tok, atoi(eq + 1));
  }
}

static RimeSessionId start(const char *schema, char *opts, const char *input) {
  RimeSessionId s = rime->create_session();
  if (!rime->select_schema(s, schema)) {
    fprintf(stderr, "cannot select schema %s\n", schema);
    exit(2);
  }
  apply_options(s, opts);
  rime->simulate_key_sequence(s, input);
  return s;
}

static int dump(const char *schema, const char *input, char *opts) {
  RimeSessionId s = start(schema, opts, input);
  RIME_STRUCT(RimeContext, ctx);
  if (rime->get_context(s, &ctx)) {
    printf("preedit: %s\n", ctx.composition.preedit ? ctx.composition.preedit : "");
    for (int i = 0; i < ctx.menu.num_candidates; i++)
      printf("%d. %s\t%s\n", i + 1, ctx.menu.candidates[i].text,
             ctx.menu.candidates[i].comment ? ctx.menu.candidates[i].comment : "");
    rime->free_context(&ctx);
  }
  rime->destroy_session(s);
  return 0;
}

static int check(const char *path) {
  FILE *f = fopen(path, "r");
  if (!f) { perror(path); return 2; }
  char line[1024];
  int failed = 0, total = 0;
  while (fgets(line, sizeof line, f)) {
    line[strcspn(line, "\r\n")] = 0;
    if (!line[0] || line[0] == '#') continue;
    char *field[6] = {0};
    int n = 0;
    for (char *p = line; p && n < 6; n++) {
      field[n] = p;
      p = strchr(p, '\t');
      if (p) *p++ = 0;
    }
    if (n < 5) { fprintf(stderr, "bad case: %s\n", line); failed++; continue; }
    total++;
    char opts[256];
    snprintf(opts, sizeof opts, "%s", field[2]);
    RimeSessionId s = start(field[0], opts, field[1]);
    int top = atoi(field[4]);
    int ok = 0;
    char seen[512] = "";
    RIME_STRUCT(RimeContext, ctx);
    if (rime->get_context(s, &ctx)) {
      for (int i = 0; i < ctx.menu.num_candidates && i < top; i++) {
        const char *t = ctx.menu.candidates[i].text;
        const char *c = ctx.menu.candidates[i].comment ? ctx.menu.candidates[i].comment : "";
        strncat(seen, t, sizeof seen - strlen(seen) - 2);
        strncat(seen, " ", sizeof seen - strlen(seen) - 1);
        if (strcmp(t, field[3]) == 0 && (n < 6 || strstr(c, field[5]))) ok = 1;
      }
      rime->free_context(&ctx);
    }
    rime->destroy_session(s);
    if (!ok) {
      failed++;
      printf("FAIL %s %s: want %s%s%s in top %d, got: %s\n", field[0], field[1], field[3],
             n >= 6 ? " with comment " : "", n >= 6 ? field[5] : "", top, seen);
    }
  }
  fclose(f);
  printf("%d/%d cases passed\n", total - failed, total);
  return failed ? 1 : 0;
}

int main(int argc, char **argv) {
  if (argc < 4) {
    fprintf(stderr, "usage: %s shared user cases.txt | -d schema input [opts]\n", argv[0]);
    return 2;
  }
  rime = rime_get_api();
  RIME_STRUCT(RimeTraits, traits);
  traits.shared_data_dir = argv[1];
  traits.user_data_dir = argv[2];
  traits.distribution_name = "Lychee test";
  traits.distribution_code_name = "lychee-test";
  traits.distribution_version = "1";
  traits.app_name = "rime.lychee-test";
  traits.min_log_level = 2;
  rime->setup(&traits);
  rime->initialize(&traits);
  if (rime->start_maintenance(True)) rime->join_maintenance_thread();
  int rc;
  if (strcmp(argv[3], "-d") == 0 && argc >= 6)
    rc = dump(argv[4], argv[5], argc >= 7 ? argv[6] : NULL);
  else
    rc = check(argv[3]);
  rime->finalize();
  return rc;
}
