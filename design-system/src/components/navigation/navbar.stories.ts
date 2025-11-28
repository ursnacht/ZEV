import type { Meta, StoryObj } from '@storybook/angular';

const meta: Meta = {
  title: 'Components/Navigation',
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => ({
    template: `
      <nav class="zev-navbar">
        <div class="zev-navbar__container">
          <h1 class="zev-navbar__title">ZEV Management</h1>
          <ul class="zev-navbar__menu">
            <li><a href="#" class="zev-navbar__link zev-navbar__link--active">Upload</a></li>
            <li><a href="#" class="zev-navbar__link">Einheiten</a></li>
            <li><a href="#" class="zev-navbar__link">Berechnung</a></li>
            <li><a href="#" class="zev-navbar__link">Diagramm</a></li>
          </ul>
        </div>
      </nav>
    `,
  }),
};

export const WithHover: Story = {
  render: () => ({
    template: `
      <nav class="zev-navbar">
        <div class="zev-navbar__container">
          <h1 class="zev-navbar__title">ZEV Management</h1>
          <ul class="zev-navbar__menu">
            <li><a href="#" class="zev-navbar__link">Upload</a></li>
            <li><a href="#" class="zev-navbar__link">Einheiten</a></li>
            <li><a href="#" class="zev-navbar__link">Berechnung</a></li>
            <li><a href="#" class="zev-navbar__link">Diagramm</a></li>
          </ul>
        </div>
      </nav>
      <div style="padding: 20px; background: white;">
        <p>Hover über die Navigation-Links um den Effekt zu sehen.</p>
      </div>
    `,
  }),
};
