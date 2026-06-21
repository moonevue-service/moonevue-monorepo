'use client';

import { Flex, Spin, Typography } from 'antd';
import { LoadingOutlined } from '@ant-design/icons';

const { Text } = Typography;

export default function DashboardLoading() {
  return (
    <Flex
      vertical
      align="center"
      justify="center"
      gap={16}
      style={{ minHeight: 'calc(100vh - 160px)' }}
    >
      <Spin indicator={<LoadingOutlined style={{ fontSize: 32 }} spin />} />
      <Text type="secondary">Carregando…</Text>
    </Flex>
  );
}
