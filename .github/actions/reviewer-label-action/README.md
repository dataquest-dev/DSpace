# Reviewer Label Management Action

A GitHub Action that automatically manages labels on issues and pull requests based on reviewer assignments and review completion.

## Features

- Applies a configurable label when a specific reviewer is assigned to an issue or requested to review a PR
- Removes the assigned label and applies a completion label when the reviewer submits their review
- Configurable for any reviewer username and any label names

## Usage

### Basic Usage

```yaml
- name: Apply reviewer labels
  uses: ./.github/actions/reviewer-label-action
  with:
    target-reviewer: 'reviewer-username'
    assigned-label: 'under-review'
    completed-label: 'review-completed'
    github-token: ${{ secrets.GITHUB_TOKEN }}
```

### Inputs

| Input | Description | Required | Default |
|-------|-------------|----------|---------|
| `target-reviewer` | The GitHub username of the reviewer to watch for | Yes | - |
| `assigned-label` | Label to apply when the target reviewer is assigned | Yes | - |
| `completed-label` | Label to apply when the target reviewer completes their review | Yes | - |
| `github-token` | GitHub token for API access | Yes | `${{ github.token }}` |

### Triggered Events

This action responds to the following GitHub events:

- `pull_request_review_requested` - When a reviewer is requested on a PR
- `pull_request_review` - When a reviewer submits a review
- `issues` - When someone is assigned to an issue

## Configuration

To use this action in your repository:

1. Copy the action to `.github/actions/reviewer-label-action/`
2. Create a workflow file in `.github/workflows/` that uses the action
3. Configure the inputs with your desired reviewer username and label names
4. Ensure the workflow has the necessary permissions (`issues: write`, `pull-requests: write`)

## Example Workflow

```yaml
name: Reviewer Label Management

on:
  pull_request_review_requested:
    types: [requested]
  pull_request_review:
    types: [submitted]
  issues:
    types: [assigned]

permissions:
  issues: write
  pull-requests: write

jobs:
  manage-reviewer-labels:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Apply reviewer labels
        uses: ./.github/actions/reviewer-label-action
        with:
          target-reviewer: 'john-doe'  # Replace with actual reviewer username
          assigned-label: 'under-review'
          completed-label: 'review-completed'
          github-token: ${{ secrets.GITHUB_TOKEN }}
```

## Multiple Reviewers

To handle multiple reviewers, create separate workflow jobs or use a matrix strategy:

```yaml
strategy:
  matrix:
    reviewer-config:
      - reviewer: 'john-doe'
        assigned: 'john-reviewing'
        completed: 'john-reviewed'
      - reviewer: 'jane-smith'
        assigned: 'jane-reviewing'
        completed: 'jane-reviewed'
        
steps:
  - name: Apply reviewer labels
    uses: ./.github/actions/reviewer-label-action
    with:
      target-reviewer: ${{ matrix.reviewer-config.reviewer }}
      assigned-label: ${{ matrix.reviewer-config.assigned }}
      completed-label: ${{ matrix.reviewer-config.completed }}
      github-token: ${{ secrets.GITHUB_TOKEN }}
```