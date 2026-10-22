---
layout: default
title: Reflections
---

# Reflections

## AI Security

I want to share about a unique attack vector that we considered. Our
staff-facing AI feature turns an uploaded lecture PDF into a topic map, and we
were quite pleased with the review step we put in front of it. A generated map
arrives as pending_review, and only course staff can approve it.

The problem surfaced while we were specifying the student feature, when we had
to write down where its retrieval actually gets its hints from. The answer was
the approved maps. Written out as a sequence, it read: untrusted PDF, then
model output, then a human approval, then input to a second AI feature. Each
step on its own was something we had deliberately designed. Together they were
a path for attacker-controlled text to arrive somewhere trusted, and the
approval was the step doing the promoting. We had built the gate as a defence
without noticing it was also the only thing in the system that upgrades
untrusted content to approved content.

We decided instead to change what approval grants instead of trying to make the
check stronger. An approved map now only feeds server-side relevance scoring:
we count how many of the question's terms appear in it and use that as a
multiplier on that PDF's own pages, capped at 3x. The map's text is never put
into a prompt, never sent to the gateway, and never shown to a student. The
most a fully attacker-controlled map achieves is nudging which pages of its own
document get retrieved, and those pages still have to survive per-sentence
citation checking. We also bound approval to a digest of the PDF bytes, so
re-uploading different content under the same filename drops the map rather
than inheriting its approved status, and made maps whose citations don't
resolve impossible to approve at all rather than merely flagged.

The lesson I took from this is to ask what an approval grants, separately from
what it checks. Ours was checking "is this a fair summary of the lecture" and
granting "this may influence another AI feature", and the gap between those two
sentences was the entire vulnerability. Narrowing the privilege on the far side
of the gate turned out to be much easier, and much more durable, than improving
the review in front of it. A human in the loop is only a control if being
approved doesn't buy very much.

## Multi-Agent SE

### Specialized Agents

For this project, instead of copying and directly using the recommendations
from the lecture slides, we decided that we wanted to build our own multi-agent
system by scratch. The rationale for this was that we felt like jumping
immediately to a "proven solution" diminishes the potential learning, because
we wouldn't get to experience the pitfalls that led people to develop the best
practices in the first place. Since this was just a low-risk school project, we
were happy to take the opportunity to try, fail and slowly iterate.

We made rookie mistakes almost immediately. When designing the roster of
specialised agents, we landed on this configuration: a product manager agent,
to write features and requirements which would be approved by humans; a UI
designer agent, who is in charge of planning the interface layout and
confirming the components that are to be used; an engineer agent, who should
implement the features; and a tester agent, who independently verifies the
implementation against its requirements and reports defects with evidence. It
seemed like a good division of tasks, so after reviewing the specs document
written by the product manager agent, we gave the go ahead to begin
implementation.

When the task ended, we realised how big of a mistake we had made. Because the
engineer agent did not have any technical specifications when it got to work,
it ended up putting nearly everything in main.tsx (amongst a whole host of
other code quality issues). The feature could run, but the file became
difficult to navigate and review, and it was unclear where future work should
go. I had assumed that asking an engineer to implement a feature was enough. It
turns out that without a clear and technically focused implementation plan, an
engineer agent could satisfy the visible request while making a poor structural
choice. That was our first big lesson - the understanding that non-technical
specifications do not substitute for technical specifications, and the
importance of having an architect agent.

That said, aside from this one issue, we otherwise felt that we experienced
immense benefits from setting up the specialised agents to handle distinct
tasks. Compared to previous single-agent attempts where one agent might have
been in charge of the end-to-end of a feature, the artefacts and agent planning
documents were noticeably of higher quality and provided wider coverage of
scenarios. Delegating tasks to subagents also allowed us to introduce more
checks into the work. For instance, early in development, we saw cases where
the task was ended and returned by the main agent despite outstanding failing
test cases. We managed to successfully prevent this scenario by introducing a
final acceptance audit agent who reviews feature artefacts and completion
evidence to decide whether the work is ready for human review. Having a "fresh"
agent in charge of this reduces slip-ups towards the end of complex tasks where
context might be nearly full.

### Building & Testing Specialised Agents

Another big challenge we faced was with UI. In the "Improve UI agent
consistency" conversation, I described the initial UI of our webapp as
"absolutely terrible". For example, we had components in the same vertical
division being set to 3 different widths for no good reason, inconsistent
button heights, and different heading sizes. We realised that despite explicit
instructions to "maintain consistency across the whole product", the agent was
incapable of doing so without further guidelines. This ended up being a very
time consuming issue to fix as we had to go through the erroneous bits
one-by-one and request for a redo.

In hindsight, the inability of the UI agent to live up to our initial
expectations was entirely our fault. Firstly, we had failed to provide the
agent with the tools it needed to succeed, like a library of reusable
components and styles which the agent would tap on repeatedly. Secondly,
instead of manually testing the UI Agent on a smaller feature first, we
immediately used it in a multi-agent system on a complex feature. This meant
that unlike a manual invocation where the agent had greater freedom to end the
task and obtain clarifications, the UI agent in the multi-agent system was
"forced" to complete its duties and hand off to the next agent, and we were not
able to realize the issue with the agent setup until the task was complete.

After reading up on some UI agent best practices, we implemented files like
workflow/design/system.md and src/frontend/src/shared/ui.tsx which document
colors, spacing, layout widths, breakpoints, and behaviours, as well as provide
reusable UI components for the app. Subsequent UI development was much smoother
afterwards and we were able to one-shot many features. In a way, this also
corrected a misconception we had regarding multi-agent vs single-agent
development; we felt like because we were using a multi-agent system, each
agent would be "smart" and hence we could get away with providing less detail.
In reality, what we realized was that unless you want to spend a lot of time
undoing lots of work which has all coalesced upon each other at the end of a
complex and lengthy job execution - you really want to define and test agents
well and provide them with the appropriate tools to succeed, even more than if
you were using a single agent.

Unfortunately, we thought it too late to do it in this project, but one thing
we would be interested to try for MP4 is to set up a generator-verifier pattern
for the UI design. Ideally, the verifier pattern should have browser and camera
access for the ability to conduct visual inspections of the finished product.

### Agent Handoffs and Artefacts

For us, the handoff template that works includes the feature name, the exact
specification and design versions, the code revision, decisions that have been
approved, open questions, known risks, the requested deliverable, and the next
owner. For verification, it should also include the acceptance criteria and the
commands already run with their actual results. Although having such strict
documentation decreased efficiency for simpler tasks, it becomes immensely
helpful in group development because it leaves a decision trace and prevents
other developers' agents from unwittingly undoing a change that was made for
good reason.

Aside from this, one important thing we realized about handoff documents is
that they must preserve uncertainty and not just facts. When observing
end-to-end orchestrations, we found that when multiple handoffs take place, an
assumption made by earlier agents can turn into an instruction for the later
agents. For example, when building the staff-LLM Knowledge Map feature, the UI
agent assumed that if the PDF parser failed, the PDF file itself should also be
rejected and not saved, and wrote an error message explaining this issue. That
interpretation was passed to the engineer, and tester, who all conducted their
functions assuming this should be the case. It required a human review to point
out that saving the PDF file should occur even if there were issues with file
parsing.
