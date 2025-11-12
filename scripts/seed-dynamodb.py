#!/usr/bin/env python3
"""
Script para popular DynamoDB com dados de exemplo
Uso: python3 seed-dynamodb.py --environment dev
"""

import boto3
import argparse
import uuid
from datetime import datetime, timedelta
import random

# Configuração
REGIONS_BR = ['SP', 'RJ', 'MG', 'RS', 'PR', 'SC', 'BA', 'PE', 'CE', 'DF']
MATERIALS = ['Zircônia', 'Porcelana', 'Resina', 'Metal', 'E-max', 'PMMA']
LAB_NAMES = [
    'Lab Dental Excellence',
    'Protética Moderna',
    'Dental Art Studio',
    'Lab Precision',
    'Smile Lab',
    'Protética Digital',
    'Lab Master Dental',
    'Odonto Lab Pro',
    'Lab Dental Tech',
    'Protética Premium'
]

def get_dynamodb_client(environment):
    """Cria cliente DynamoDB"""
    return boto3.resource('dynamodb', region_name='us-east-1')

def seed_labs(dynamodb, environment, count=10):
    """Popula tabela de laboratórios"""
    table = dynamodb.Table(f'protheo-labs-{environment}')
    
    print(f"Seeding {count} labs...")
    
    for i in range(count):
        lab_id = str(uuid.uuid4())
        uf = random.choice(REGIONS_BR)
        materials = random.sample(MATERIALS, k=random.randint(2, 4))
        sla_days = random.choice([5, 7, 10, 14, 21])
        
        item = {
            'labId': lab_id,
            'name': LAB_NAMES[i],
            'uf': uf,
            'city': f'Cidade {i+1}',
            'materials': materials,
            'sla': sla_days,
            'averageRating': round(random.uniform(3.5, 5.0), 2),
            'totalReviews': random.randint(5, 100),
            'description': f'Laboratório especializado em próteses dentárias de alta qualidade.',
            'phone': f'(11) 9{random.randint(1000, 9999)}-{random.randint(1000, 9999)}',
            'email': f'contato@{LAB_NAMES[i].lower().replace(" ", "")}.com.br',
            'stripeAccountId': f'acct_lab_{i+1}',
            'active': True,
            'createdAt': datetime.now().isoformat()
        }
        
        table.put_item(Item=item)
        print(f"  ✓ Created lab: {item['name']} ({uf})")
    
    print(f"✅ {count} labs created successfully!\n")
    return table

def seed_cases(dynamodb, environment, labs_table, count=100):
    """Popula tabela de casos"""
    table = dynamodb.Table(f'protheo-cases-{environment}')
    
    # Buscar labs criados
    labs_response = labs_table.scan()
    labs = labs_response['Items']
    
    if not labs:
        print("⚠️  No labs found. Please seed labs first.")
        return
    
    print(f"Seeding {count} cases...")
    
    statuses = [
        'PENDING', 'AWAITING_BUDGET', 'BUDGET_SENT', 'IN_PROGRESS',
        'AWAITING_APPROVAL', 'APPROVED', 'PAID', 'SHIPPED', 'COMPLETED'
    ]
    
    for i in range(count):
        case_id = str(uuid.uuid4())
        dentist_id = f'dentist_{random.randint(1, 20)}'
        lab = random.choice(labs)
        status = random.choice(statuses)
        created_at = datetime.now() - timedelta(days=random.randint(1, 90))
        
        item = {
            'caseId': case_id,
            'dentistId': dentist_id,
            'labId': lab['labId'] if random.random() > 0.3 else None,
            'status': status,
            'title': f'Caso {i+1} - Prótese Dentária',
            'description': f'Prótese para paciente com necessidade de {random.choice(MATERIALS)}',
            'filesKeys': [f'cases/{case_id}/stl_{j}.stl' for j in range(random.randint(1, 3))],
            'projectFileKey': f'cases/{case_id}/final_project.stl' if status in ['AWAITING_APPROVAL', 'APPROVED', 'PAID', 'SHIPPED', 'COMPLETED'] else None,
            'revisionCount': random.randint(0, 2) if status == 'REVISION_REQUESTED' else 0,
            'createdAt': created_at.isoformat(),
            'updatedAt': datetime.now().isoformat()
        }
        
        table.put_item(Item=item)
        
        if (i + 1) % 10 == 0:
            print(f"  ✓ Created {i + 1} cases...")
    
    print(f"✅ {count} cases created successfully!\n")

def seed_budgets(dynamodb, environment, count=50):
    """Popula tabela de orçamentos"""
    table = dynamodb.Table(f'protheo-budgets-{environment}')
    
    print(f"Seeding {count} budgets...")
    
    for i in range(count):
        budget_id = str(uuid.uuid4())
        case_id = f'case_{random.randint(1, 100)}'
        lab_id = f'lab_{random.randint(1, 10)}'
        
        # Valores em centavos
        base_amount = random.randint(30000, 150000)  # R$ 300 - R$ 1500
        
        item = {
            'budgetId': budget_id,
            'caseId': case_id,
            'labId': lab_id,
            'amount': base_amount,
            'currency': 'BRL',
            'breakdown': [
                {'description': 'Material', 'amount': int(base_amount * 0.4)},
                {'description': 'Mão de obra', 'amount': int(base_amount * 0.5)},
                {'description': 'Frete', 'amount': int(base_amount * 0.1)}
            ],
            'validUntil': (datetime.now() + timedelta(days=7)).isoformat(),
            'status': random.choice(['PENDING', 'APPROVED', 'REJECTED', 'EXPIRED']),
            'createdAt': datetime.now().isoformat()
        }
        
        table.put_item(Item=item)
    
    print(f"✅ {count} budgets created successfully!\n")

def seed_reviews(dynamodb, environment, count=50):
    """Popula tabela de avaliações"""
    table = dynamodb.Table(f'protheo-reviews-{environment}')
    
    print(f"Seeding {count} reviews...")
    
    comments = [
        'Excelente trabalho, muito satisfeito!',
        'Qualidade excepcional, recomendo!',
        'Bom atendimento e prazo cumprido.',
        'Trabalho impecável, paciente muito feliz.',
        'Ótima comunicação e resultado final perfeito.'
    ]
    
    for i in range(count):
        review_id = str(uuid.uuid4())
        
        item = {
            'reviewId': review_id,
            'caseId': f'case_{random.randint(1, 100)}',
            'dentistId': f'dentist_{random.randint(1, 20)}',
            'labId': f'lab_{random.randint(1, 10)}',
            'rating': random.randint(3, 5),
            'comment': random.choice(comments),
            'createdAt': datetime.now().isoformat()
        }
        
        table.put_item(Item=item)
    
    print(f"✅ {count} reviews created successfully!\n")

def main():
    parser = argparse.ArgumentParser(description='Seed DynamoDB with sample data')
    parser.add_argument('--environment', '-e', required=True, choices=['dev', 'staging', 'prod'],
                        help='Environment to seed')
    parser.add_argument('--labs', type=int, default=10, help='Number of labs to create')
    parser.add_argument('--cases', type=int, default=100, help='Number of cases to create')
    parser.add_argument('--budgets', type=int, default=50, help='Number of budgets to create')
    parser.add_argument('--reviews', type=int, default=50, help='Number of reviews to create')
    
    args = parser.parse_args()
    
    print(f"\n🌱 Starting seed for environment: {args.environment}\n")
    
    dynamodb = get_dynamodb_client(args.environment)
    
    # Seed data
    labs_table = seed_labs(dynamodb, args.environment, args.labs)
    seed_cases(dynamodb, args.environment, labs_table, args.cases)
    seed_budgets(dynamodb, args.environment, args.budgets)
    seed_reviews(dynamodb, args.environment, args.reviews)
    
    print("✅ All data seeded successfully!")
    print(f"\n📊 Summary:")
    print(f"  - Labs: {args.labs}")
    print(f"  - Cases: {args.cases}")
    print(f"  - Budgets: {args.budgets}")
    print(f"  - Reviews: {args.reviews}")

if __name__ == '__main__':
    main()
