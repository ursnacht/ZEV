import type { Meta, StoryObj } from '@storybook/angular';

const meta: Meta = {
  title: 'Components/Button',
  tags: ['autodocs'],
  argTypes: {
    variant: {
      control: 'select',
      options: ['primary', 'secondary', 'danger'],
      description: 'Button variant style',
    },
    disabled: {
      control: 'boolean',
      description: 'Disabled state',
    },
    text: {
      control: 'text',
      description: 'Button text content',
    },
  },
};

export default meta;
type Story = StoryObj;

export const Primary: Story = {
  render: (args) => ({
    props: args,
    template: `
      <button class="zev-button zev-button--primary" [disabled]="disabled">
        {{text}}
      </button>
    `,
  }),
  args: {
    text: 'Primary Button',
    disabled: false,
  },
};

export const Secondary: Story = {
  render: (args) => ({
    props: args,
    template: `
      <button class="zev-button zev-button--secondary" [disabled]="disabled">
        {{text}}
      </button>
    `,
  }),
  args: {
    text: 'Secondary Button',
    disabled: false,
  },
};

export const Danger: Story = {
  render: (args) => ({
    props: args,
    template: `
      <button class="zev-button zev-button--danger" [disabled]="disabled">
        {{text}}
      </button>
    `,
  }),
  args: {
    text: 'Delete',
    disabled: false,
  },
};

export const Disabled: Story = {
  render: (args) => ({
    props: args,
    template: `
      <button class="zev-button zev-button--primary" disabled>
        {{text}}
      </button>
    `,
  }),
  args: {
    text: 'Disabled Button',
  },
};

export const AllVariants: Story = {
  render: () => ({
    template: `
      <div style="display: flex; gap: 10px; flex-wrap: wrap;">
        <button class="zev-button zev-button--primary">Primary</button>
        <button class="zev-button zev-button--secondary">Secondary</button>
        <button class="zev-button zev-button--danger">Danger</button>
        <button class="zev-button zev-button--primary" disabled>Disabled</button>
        <button class="zev-button zev-button--primary zev-button--compact">Compact</button>
      </div>
    `,
  }),
};
