import { openai } from '../lib/openai.js';
import { buildSystemPrompt } from '../lib/prompts.js';

export async function aiRoutes(app) {

  // --- Freeform chat ---
  app.post('/chat', {
    config: {
      rateLimit: {
        max: parseInt(process.env.RATE_LIMIT_CHAT || '30', 10),
        timeWindow: '1 hour',
        keyGenerator: (req) => req.uid,
      },
    },
  }, async (request, reply) => {
    const { context, prompt } = request.body;

    if (!prompt) {
      return reply.code(400).send({ error: 'bad_request', message: 'prompt is required.' });
    }

    const systemPrompt = buildSystemPrompt('chat', context);

    const completion = await openai.chat.completions.create({
      model: 'gpt-4o',
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: prompt },
      ],
      max_tokens: 500,
      temperature: 0.7,
    });

    return { message: completion.choices[0].message.content };
  });

  // --- Meal photo analysis ---
  app.post('/meal-photo', {
    config: {
      rateLimit: {
        max: parseInt(process.env.RATE_LIMIT_VISION || '15', 10),
        timeWindow: '1 hour',
        keyGenerator: (req) => req.uid,
      },
    },
  }, async (request, reply) => {
    const { context, image_base64 } = request.body;

    if (!image_base64) {
      return reply.code(400).send({ error: 'bad_request', message: 'image_base64 is required.' });
    }

    const systemPrompt = buildSystemPrompt('meal-photo', context);

    const completion = await openai.chat.completions.create({
      model: 'gpt-4o',
      messages: [
        { role: 'system', content: systemPrompt },
        {
          role: 'user',
          content: [
            { type: 'text', text: 'Analyze this meal.' },
            { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${image_base64}` } },
          ],
        },
      ],
      max_tokens: 500,
      temperature: 0.3,
    });

    return { message: completion.choices[0].message.content };
  });

  // --- Menu scan ---
  app.post('/menu-scan', {
    config: {
      rateLimit: {
        max: parseInt(process.env.RATE_LIMIT_VISION || '15', 10),
        timeWindow: '1 hour',
        keyGenerator: (req) => req.uid,
      },
    },
  }, async (request, reply) => {
    const { context, image_base64 } = request.body;

    if (!image_base64) {
      return reply.code(400).send({ error: 'bad_request', message: 'image_base64 is required.' });
    }

    const systemPrompt = buildSystemPrompt('menu-scan', context);

    const completion = await openai.chat.completions.create({
      model: 'gpt-4o',
      messages: [
        { role: 'system', content: systemPrompt },
        {
          role: 'user',
          content: [
            { type: 'text', text: 'Scan this menu and rank options for my goals.' },
            { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${image_base64}` } },
          ],
        },
      ],
      max_tokens: 800,
      temperature: 0.4,
    });

    return { message: completion.choices[0].message.content };
  });

  // --- Fridge scan ---
  app.post('/fridge-scan', {
    config: {
      rateLimit: {
        max: parseInt(process.env.RATE_LIMIT_VISION || '15', 10),
        timeWindow: '1 hour',
        keyGenerator: (req) => req.uid,
      },
    },
  }, async (request, reply) => {
    const { context, image_base64 } = request.body;

    if (!image_base64) {
      return reply.code(400).send({ error: 'bad_request', message: 'image_base64 is required.' });
    }

    const systemPrompt = buildSystemPrompt('fridge-scan', context);

    const completion = await openai.chat.completions.create({
      model: 'gpt-4o',
      messages: [
        { role: 'system', content: systemPrompt },
        {
          role: 'user',
          content: [
            { type: 'text', text: 'What can I make from what you see? Rank by goal alignment.' },
            { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${image_base64}` } },
          ],
        },
      ],
      max_tokens: 800,
      temperature: 0.4,
    });

    return { message: completion.choices[0].message.content };
  });

  // --- Weekly insights ---
  app.post('/insights', {
    config: {
      rateLimit: {
        max: 3,
        timeWindow: '1 day',
        keyGenerator: (req) => req.uid,
      },
    },
  }, async (request, reply) => {
    const { context } = request.body;

    const systemPrompt = buildSystemPrompt('insights', context);

    const completion = await openai.chat.completions.create({
      model: 'gpt-4o',
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: `Here is my data for pattern analysis:\n${JSON.stringify(context, null, 2)}` },
      ],
      max_tokens: 1000,
      temperature: 0.5,
    });

    return { message: completion.choices[0].message.content };
  });
}
